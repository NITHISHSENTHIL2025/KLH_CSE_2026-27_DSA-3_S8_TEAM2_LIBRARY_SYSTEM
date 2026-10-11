package com.intellilib.server.dsa;
public final class RabinKarpMatcher {
    private RabinKarpMatcher(){}
    public static boolean contains(String text,String pattern){ if(pattern==null||pattern.isEmpty())return true; if(text==null||pattern.length()>text.length())return false; String t=text.toLowerCase(),p=pattern.toLowerCase(); long mod=1_000_000_007L, base=257, ph=0,th=0,pow=1; for(int i=0;i<p.length();i++){ph=(ph*base+p.charAt(i))%mod;th=(th*base+t.charAt(i))%mod;if(i<p.length()-1)pow=pow*base%mod;} for(int i=0;i<=t.length()-p.length();i++){ if(ph==th&&t.regionMatches(i,p,0,p.length()))return true; if(i<t.length()-p.length()){th=(th-t.charAt(i)*pow)%mod;if(th<0)th+=mod;th=(th*base+t.charAt(i+p.length()))%mod;}} return false; }
}
