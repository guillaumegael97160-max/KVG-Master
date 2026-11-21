package fr.kvg.malory;
import org.json.JSONObject;
import org.json.JSONArray;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import javax.net.ssl.HttpsURLConnection;

final class ServiceKvg {
 private static final String URL_API="https://script.google.com/macros/s/AKfycbyezwCIzg2WYtyrKUfM6LfQnveCGH2mlLou-q1-8tWAEwxG41AcQ0YwpTbrkIkmH6ZN/exec";
 static JSONObject appeler(String action,JSONArray args)throws Exception{
  byte[] corps=new JSONObject().put("action",action).put("args",args).toString().getBytes(StandardCharsets.UTF_8);
  URL url=new URL(URL_API);boolean post=true;
  for(int saut=0;saut<4;saut++){
   HttpsURLConnection c=(HttpsURLConnection)url.openConnection();c.setInstanceFollowRedirects(false);c.setUseCaches(false);c.setConnectTimeout(15000);c.setReadTimeout(90000);c.setRequestProperty("Accept","application/json");
   try{
    if(post){c.setRequestMethod("POST");c.setRequestProperty("Content-Type","text/plain;charset=UTF-8");c.setDoOutput(true);c.setFixedLengthStreamingMode(corps.length);try(OutputStream out=c.getOutputStream()){out.write(corps);}}
    int status=c.getResponseCode();
    if(status==301||status==302||status==303){URL cible=new URL(url,c.getHeaderField("Location"));String host=cible.getHost();
     if(!cible.getProtocol().equals("https")||!(host.equals("script.googleusercontent.com")||host.endsWith(".googleusercontent.com")||host.equals("script.google.com")))throw new IOException("Redirection inattendue.");
     url=cible;post=false;continue;}
    if(status!=200)throw new IOException("Service KVG indisponible ("+status+").");
    try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buf=new byte[16384];int n;while((n=in.read(buf))!=-1){if(out.size()+n>12000000)throw new IOException("Réponse trop volumineuse.");out.write(buf,0,n);}return new JSONObject(out.toString("UTF-8"));}
   }finally{c.disconnect();}
  }
  throw new IOException("Le service a redirigé la demande trop de fois.");
 }
}
