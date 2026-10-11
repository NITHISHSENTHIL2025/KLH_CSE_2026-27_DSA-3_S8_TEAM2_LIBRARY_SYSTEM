package com.intellilib.server.dsa;

@SuppressWarnings("unchecked")
public final class StringHashTable<V> {
    private String[] keys;
    private Object[] values;
    private boolean[] used;
    private int size;
    public StringHashTable(){ this(32); }
    public StringHashTable(int cap){ int n=1; while(n<cap)n<<=1; keys=new String[n]; values=new Object[n]; used=new boolean[n]; }
    public int size(){return size;}
    public V get(String key){ if(key==null)return null; int i=find(key); return i<0?null:(V)values[i]; }
    public void put(String key,V value){ if((size+1)*10>keys.length*7) resize(); insert(key,value); }
    private void insert(String key,V value){ int mask=keys.length-1, i=hash(key)&mask; while(used[i]){ if(keys[i].equals(key)){ values[i]=value; return;} i=(i+1)&mask;} used[i]=true; keys[i]=key; values[i]=value; size++; }
    private int find(String key){ int mask=keys.length-1, i=hash(key)&mask, start=i; while(used[i]){ if(keys[i].equals(key))return i; i=(i+1)&mask; if(i==start)break;} return -1; }
    private void resize(){ String[] ok=keys; Object[] ov=values; boolean[] ou=used; keys=new String[ok.length*2]; values=new Object[ov.length*2]; used=new boolean[ou.length*2]; size=0; for(int i=0;i<ok.length;i++) if(ou[i]) insert(ok[i],(V)ov[i]); }
    private static int hash(String s){ int h=0x811C9DC5; for(int i=0;i<s.length();i++){ h^=s.charAt(i); h*=0x01000193;} h^=h>>>16; return h; }
}
