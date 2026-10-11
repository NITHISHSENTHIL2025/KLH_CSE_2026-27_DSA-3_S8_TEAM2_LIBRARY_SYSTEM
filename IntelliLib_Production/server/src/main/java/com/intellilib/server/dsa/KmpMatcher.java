package com.intellilib.server.dsa;
public final class KmpMatcher {
    private KmpMatcher(){}
    public static boolean containsIgnoreCase(String text,String pattern){ if(pattern==null||pattern.isEmpty())return true; if(text==null)return false; return contains(text.toLowerCase(),pattern.toLowerCase()); }
    public static boolean contains(String t,String p){ if(p.isEmpty())return true; int[] lps=new int[p.length()]; for(int i=1,len=0;i<p.length();){ if(p.charAt(i)==p.charAt(len))lps[i++]=++len; else if(len>0)len=lps[len-1]; else lps[i++]=0; } for(int i=0,j=0;i<t.length();){ if(t.charAt(i)==p.charAt(j)){i++;j++; if(j==p.length())return true;} else if(j>0)j=lps[j-1]; else i++; } return false; }
}
