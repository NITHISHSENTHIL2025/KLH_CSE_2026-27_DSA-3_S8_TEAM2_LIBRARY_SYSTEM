package com.intellilib.server.dsa;

public final class TrieIndex {
    private static final class Node {
        final Node[] next = new Node[38];
        String terminal;
    }
    private final Node root = new Node();

    public void insert(String value){
        if(value==null||value.isBlank())return;
        String n=normalize(value); Node cur=root;
        for(int i=0;i<n.length();i++){ int x=idx(n.charAt(i)); if(x<0)continue; if(cur.next[x]==null)cur.next[x]=new Node(); cur=cur.next[x]; }
        if(cur.terminal==null)cur.terminal=value;
    }

    public String[] suggest(String prefix,int limit){
        String n=normalize(prefix); Node cur=root;
        for(int i=0;i<n.length();i++){ int x=idx(n.charAt(i)); if(x<0)continue; if(cur.next[x]==null)return new String[0]; cur=cur.next[x]; }
        String[] out=new String[Math.max(1,limit)]; int[] count={0}; collect(cur,out,count,limit); String[] r=new String[count[0]]; System.arraycopy(out,0,r,0,count[0]); return r;
    }
    private void collect(Node n,String[] out,int[] c,int limit){ if(n==null||c[0]>=limit)return; if(n.terminal!=null)out[c[0]++]=n.terminal; for(int i=0;i<n.next.length&&c[0]<limit;i++)collect(n.next[i],out,c,limit); }
    private static String normalize(String s){ return s.toLowerCase().trim().replaceAll("\\s+"," "); }
    private static int idx(char c){ if(c>='a'&&c<='z')return c-'a'; if(c>='0'&&c<='9')return 26+c-'0'; if(c==' ')return 36; if(c=='-')return 37; return -1; }
}
