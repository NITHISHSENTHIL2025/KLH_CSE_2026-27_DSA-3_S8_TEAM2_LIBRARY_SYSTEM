package com.intellilib.server.dsa;
import org.junit.jupiter.api.Test; import static org.junit.jupiter.api.Assertions.*;
class DsaCoreTest {
 @Test void hashWorks(){StringHashTable<Integer> h=new StringHashTable<>();for(int i=0;i<1000;i++)h.put("k"+i,i);assertEquals(777,h.get("k777"));}
 @Test void trieSuggests(){TrieIndex t=new TrieIndex();t.insert("Algorithm Design");t.insert("Algorithms in Java");assertEquals(2,t.suggest("algo",10).length);}
 @Test void avlBrowse(){AvlBookIndex a=new AvlBookIndex();a.add("Beta",2);a.add("Alpha",1);a.add("Algebra",3);assertEquals(2,a.titlesStartingWith("Al",10).length);}
 @Test void kmp(){assertTrue(KmpMatcher.contains("abcxabcdabxabcdabcdabcy","abcdabcy"));}
 @Test void rabin(){assertTrue(RabinKarpMatcher.contains("Advanced Data Structures","data"));}
 @Test void edit(){assertEquals(1,EditDistance.distance("algorithm","algoritm"));}
}
