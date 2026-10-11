package com.intellilib.server.search;

import com.intellilib.server.dsa.*;
import com.intellilib.server.model.ApiModels.BookSummary;
import com.intellilib.server.model.ApiModels.SearchResponse;
import com.intellilib.server.model.ApiModels.Suggestion;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Service
public class SearchEngine {
    private final JdbcTemplate jdbc;
    private final ReentrantReadWriteLock lock=new ReentrantReadWriteLock();
    private SearchBook[] books=new SearchBook[0];
    private StringHashTable<Integer> isbnIndex=new StringHashTable<>();
    private StringHashTable<IntList> tokenIndex=new StringHashTable<>();
    private TrieIndex trie=new TrieIndex();
    private AvlBookIndex avl=new AvlBookIndex();
    private BookGraph graph=new BookGraph(0);
    private final Map<Long,Integer> idToIndex=new HashMap<>();

    public SearchEngine(JdbcTemplate jdbc){this.jdbc=jdbc;}
    @PostConstruct public void init(){ rebuild(); }

    public void rebuild(){
        lock.writeLock().lock();
        try{
            List<SearchBook> rows=jdbc.query("""
                SELECT b.*, COUNT(c.id) total_copies,
                       COUNT(c.id) FILTER (WHERE c.status='AVAILABLE') available_copies
                FROM books b LEFT JOIN book_copies c ON c.book_id=b.id
                WHERE b.active=TRUE GROUP BY b.id ORDER BY b.id
            """,(rs,n)->new SearchBook(rs.getLong("id"),rs.getString("title"),rs.getString("author"),rs.getString("isbn"),
                    rs.getString("category"),rs.getString("keywords"),rs.getString("resource_type"),rs.getString("publisher"),
                    rs.getObject("publication_year")==null?0:rs.getInt("publication_year"),rs.getString("description"),rs.getString("language"),
                    rs.getDouble("rating"),rs.getInt("total_copies"),rs.getInt("available_copies")));
            books=rows.toArray(SearchBook[]::new); isbnIndex=new StringHashTable<>(books.length*2+16); tokenIndex=new StringHashTable<>(books.length*8+64); trie=new TrieIndex(); avl=new AvlBookIndex(); graph=new BookGraph(books.length); idToIndex.clear();
            Map<String,List<Integer>> buckets=new HashMap<>();
            for(int i=0;i<books.length;i++){
                SearchBook b=books[i]; idToIndex.put(b.id(),i);
                if(b.isbn()!=null&&!b.isbn().isBlank())isbnIndex.put(norm(b.isbn()),i);
                avl.add(b.title(),i); trie.insert(b.title()); trie.insert(b.author());
                indexTokens(i,b.title()); indexTokens(i,b.author()); indexTokens(i,b.category()); indexTokens(i,b.keywords());
                bucket(buckets,"c:"+norm(b.category()),i); bucket(buckets,"a:"+norm(b.author()),i);
                if(b.keywords()!=null) for(String k:b.keywords().split("[,;]")) if(!k.isBlank()) bucket(buckets,"k:"+norm(k),i);
            }
            for(List<Integer> list:buckets.values()){
                int cap=Math.min(list.size(),12);
                for(int a=0;a<cap;a++) for(int b=a+1;b<cap;b++) graph.connect(list.get(a),list.get(b));
            }
        }finally{lock.writeLock().unlock();}
    }

    public void refreshAvailability(long bookId){
        lock.writeLock().lock();
        try{
            Integer idx=idToIndex.get(bookId); if(idx==null)return;
            int[] counts=jdbc.queryForObject("SELECT COUNT(*) total, COUNT(*) FILTER(WHERE status='AVAILABLE') available FROM book_copies WHERE book_id=?",
                    (rs,n)->new int[]{rs.getInt("total"),rs.getInt("available")},bookId);
            if(counts==null)return;
            SearchBook b=books[idx];
            books[idx]=new SearchBook(b.id(),b.title(),b.author(),b.isbn(),b.category(),b.keywords(),b.resourceType(),b.publisher(),b.year(),b.description(),b.language(),b.rating(),counts[0],counts[1]);
        }finally{lock.writeLock().unlock();}
    }

    public SearchResponse search(String query,int limit){
        long start=System.nanoTime(); query=query==null?"":query.trim(); if(query.isEmpty())return new SearchResponse(List.of(),0,0);
        lock.readLock().lock();
        try{
            LinkedHashMap<Integer,Double> scores=new LinkedHashMap<>(); String q=norm(query);
            Integer isbn=isbnIndex.get(q); if(isbn!=null)scores.put(isbn,100.0);
            String[] parts=q.split("\\s+");
            for(String p:parts){ IntList list=tokenIndex.get(p); if(list!=null)for(int i=0;i<list.size();i++)scores.merge(list.get(i),20.0,Double::sum); }
            if(scores.isEmpty()){
                // typo recovery across a bounded token vocabulary from titles/categories
                for(int i=0;i<books.length&&scores.size()<limit*4;i++){
                    SearchBook b=books[i]; if(fuzzyHit(q,b.title())||fuzzyHit(q,b.author()))scores.put(i,12.0);
                }
            }
            ArrayList<Map.Entry<Integer,Double>> ranked=new ArrayList<>(scores.entrySet());
            for(var e:ranked){ SearchBook b=books[e.getKey()]; double s=e.getValue(); String title=norm(b.title()),author=norm(b.author()); if(title.equals(q))s+=80; else if(title.startsWith(q))s+=40; if(author.equals(q))s+=35; if(KmpMatcher.containsIgnoreCase(b.searchable(),query))s+=15; s+=Math.min(5,b.rating()); e.setValue(s); }
            ranked.sort((a,b)->Double.compare(b.getValue(),a.getValue()));
            ArrayList<BookSummary> out=new ArrayList<>(); int n=Math.min(limit,ranked.size()); for(int i=0;i<n;i++)out.add(toSummary(books[ranked.get(i).getKey()],ranked.get(i).getValue()));
            long ms=(System.nanoTime()-start)/1_000_000L; return new SearchResponse(out,ms,ranked.size());
        }finally{lock.readLock().unlock();}
    }

    public List<Suggestion> suggest(String prefix,int limit){ lock.readLock().lock(); try{ String[] arr=trie.suggest(prefix,limit); ArrayList<Suggestion> out=new ArrayList<>(); for(String s:arr)out.add(new Suggestion(s,"suggestion",null)); return out;}finally{lock.readLock().unlock();} }
    public List<BookSummary> related(long bookId,int limit){ lock.readLock().lock(); try{Integer idx=idToIndex.get(bookId); if(idx==null)return List.of(); int[] n=graph.neighbors(idx,limit); ArrayList<BookSummary> out=new ArrayList<>(); for(int x:n)out.add(toSummary(books[x],0)); return out;}finally{lock.readLock().unlock();} }
    public List<BookSummary> browse(String prefix,int limit){ lock.readLock().lock(); try{ int[] ids=avl.titlesStartingWith(prefix,limit); ArrayList<BookSummary> out=new ArrayList<>(); for(int i:ids)out.add(toSummary(books[i],0)); return out;}finally{lock.readLock().unlock();} }
    public BookSummary get(long id){ lock.readLock().lock(); try{Integer i=idToIndex.get(id); return i==null?null:toSummary(books[i],0);}finally{lock.readLock().unlock();} }

    private void indexTokens(int idx,String text){ if(text==null)return; for(String t:norm(text).split("[^a-z0-9]+")){ if(t.length()<2)continue; IntList l=tokenIndex.get(t); if(l==null){l=new IntList();tokenIndex.put(t,l);} l.addUnique(idx);} }
    private static void bucket(Map<String,List<Integer>> m,String k,int i){m.computeIfAbsent(k,x->new ArrayList<>()).add(i);} private static String norm(String s){return s==null?"":s.toLowerCase().trim().replaceAll("\\s+"," ");}
    private static boolean fuzzyHit(String q,String value){ if(q.length()>48||value==null)return false; String[] words=norm(value).split("\\s+"); for(String w:words){int max=Math.max(1,Math.min(2,q.length()/4)); if(Math.abs(w.length()-q.length())<=max&&EditDistance.distance(q,w)<=max)return true;} return false; }
    private static BookSummary toSummary(SearchBook b,double rel){return new BookSummary(b.id(),b.title(),b.author(),b.isbn(),b.category(),b.resourceType(),b.year(),b.publisher(),b.keywords(),b.description(),b.language(),b.rating(),b.totalCopies(),b.availableCopies(),rel);}
}
