package com.intellilib.server.dsa;

public final class IntList {
    private int[] data = new int[8];
    private int size;
    public void add(int value) { ensure(size+1); data[size++]=value; }
    public boolean contains(int value) { for(int i=0;i<size;i++) if(data[i]==value) return true; return false; }
    public void addUnique(int value) { if(!contains(value)) add(value); }
    public int get(int i) { if(i<0||i>=size) throw new IndexOutOfBoundsException(); return data[i]; }
    public int size() { return size; }
    public int[] toArray(){ int[] out=new int[size]; System.arraycopy(data,0,out,0,size); return out; }
    private void ensure(int n){ if(n<=data.length)return; int[] x=new int[data.length*2]; System.arraycopy(data,0,x,0,size); data=x; }
}
