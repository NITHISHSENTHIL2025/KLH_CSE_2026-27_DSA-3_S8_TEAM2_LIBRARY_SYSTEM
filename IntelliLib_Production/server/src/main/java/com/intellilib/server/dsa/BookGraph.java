package com.intellilib.server.dsa;

public final class BookGraph {
    private final IntList[] adj;
    public BookGraph(int size){adj=new IntList[size]; for(int i=0;i<size;i++)adj[i]=new IntList();}
    public void connect(int a,int b){ if(a<0||b<0||a>=adj.length||b>=adj.length||a==b)return; adj[a].addUnique(b); adj[b].addUnique(a); }
    public int[] neighbors(int i,int limit){ if(i<0||i>=adj.length)return new int[0]; int n=Math.min(limit,adj[i].size()); int[] out=new int[n]; for(int x=0;x<n;x++)out[x]=adj[i].get(x); return out; }
}
