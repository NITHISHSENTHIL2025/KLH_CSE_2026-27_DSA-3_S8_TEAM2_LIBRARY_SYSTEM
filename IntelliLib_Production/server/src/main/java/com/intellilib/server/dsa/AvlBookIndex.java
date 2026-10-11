package com.intellilib.server.dsa;

public final class AvlBookIndex {
    private static final class Node {
        String key; int bookIndex,height=1; Node left,right;
        Node(String k,int i){key=k;bookIndex=i;}
    }
    private Node root;
    public void add(String title,int bookIndex){ root=insert(root,normalize(title)+"#"+bookIndex,bookIndex); }
    public int[] titlesStartingWith(String prefix,int limit){ IntList out=new IntList(); String p=normalize(prefix); range(root,p,p+'\uffff',out,limit); return out.toArray(); }
    public int[] first(int limit){ IntList out=new IntList(); inorder(root,out,limit); return out.toArray(); }
    private Node insert(Node n,String k,int idx){ if(n==null)return new Node(k,idx); int c=k.compareTo(n.key); if(c<0)n.left=insert(n.left,k,idx); else n.right=insert(n.right,k,idx); upd(n); int b=bal(n); if(b>1&&k.compareTo(n.left.key)<0)return rotR(n); if(b<-1&&k.compareTo(n.right.key)>0)return rotL(n); if(b>1){n.left=rotL(n.left);return rotR(n);} if(b<-1){n.right=rotR(n.right);return rotL(n);} return n; }
    private void range(Node n,String lo,String hi,IntList out,int limit){ if(n==null||out.size()>=limit)return; if(n.key.compareTo(lo)>=0)range(n.left,lo,hi,out,limit); if(out.size()<limit&&n.key.compareTo(lo)>=0&&n.key.compareTo(hi)<=0)out.add(n.bookIndex); if(n.key.compareTo(hi)<=0)range(n.right,lo,hi,out,limit); }
    private void inorder(Node n,IntList o,int limit){ if(n==null||o.size()>=limit)return; inorder(n.left,o,limit); if(o.size()<limit)o.add(n.bookIndex); inorder(n.right,o,limit); }
    private static int h(Node n){return n==null?0:n.height;} private static void upd(Node n){n.height=Math.max(h(n.left),h(n.right))+1;} private static int bal(Node n){return h(n.left)-h(n.right);} private static Node rotR(Node y){Node x=y.left,t=x.right;x.right=y;y.left=t;upd(y);upd(x);return x;} private static Node rotL(Node x){Node y=x.right,t=y.left;y.left=x;x.right=t;upd(x);upd(y);return y;} private static String normalize(String s){return s==null?"":s.toLowerCase().trim();}
}
