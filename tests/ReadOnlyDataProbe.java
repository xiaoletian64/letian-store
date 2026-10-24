package test;
import android.app.Application;
import android.app.Instrumentation;
import android.content.Context;
import android.os.Bundle;
import java.io.*;
import java.security.MessageDigest;
import org.json.*;
/** Store data backup only. Does not initialize the store application or restore files. */
public final class ReadOnlyDataProbe extends Instrumentation {
 public Application newApplication(ClassLoader l,String n,Context c)throws InstantiationException,IllegalAccessException,ClassNotFoundException{
  if(!c.getPackageName().equals("site.zhangyue.store"))throw new SecurityException();
  return super.newApplication(l,Application.class.getName(),c);
 }
 public void callApplicationOnCreate(Application a){}
 public void onCreate(Bundle b){start();}
 static String hash(File f)throws Exception{MessageDigest d=MessageDigest.getInstance("SHA-256");try(InputStream in=new FileInputStream(f)){byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)d.update(b,0,n);}StringBuilder s=new StringBuilder();for(byte b:d.digest())s.append(String.format("%02x",b&255));return s.toString();}
 static long bytes(File f)throws Exception{if(!f.getAbsolutePath().equals(f.getCanonicalPath()))return 0;if(f.isFile())return f.length();File[] children=f.listFiles();long n=0;if(children!=null)for(File child:children)n+=bytes(child);return n;}
 static void copy(File src,File dst,JSONArray report,String name)throws Exception{
  if(!src.getAbsolutePath().equals(src.getCanonicalPath()))throw new IOException("symlink source");
  if(src.isDirectory()){if(dst.exists()||!dst.mkdirs())throw new IOException("new directory required");File[] children=src.listFiles();if(children==null)throw new IOException("unreadable source");for(File f:children)copy(f,new File(dst,f.getName()),report,name+"/"+f.getName());return;}
  if(dst.exists())throw new IOException("refuse overwrite");String before=hash(src);
  try(InputStream in=new FileInputStream(src);FileOutputStream out=new FileOutputStream(dst)){byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)out.write(b,0,n);out.getFD().sync();}
  if(!before.equals(hash(src))||!before.equals(hash(dst)))throw new IOException("source changed");report.put(new JSONObject().put("path",name).put("bytes",src.length()).put("sha256",before));
 }
 public void onStart(){Bundle result=new Bundle();try{
  Context c=getTargetContext();File data=c.getDataDir().getCanonicalFile(),external=c.getExternalFilesDir(null);File out=new File(external,"readonly-store-"+System.currentTimeMillis());if(out.exists()||!out.mkdirs())throw new IOException("new evidence directory required");
  if(out.getUsableSpace()<bytes(data)+67108864L)throw new IOException("insufficient backup space");JSONArray list=new JSONArray();File[] children=data.listFiles();if(children==null)throw new IOException("unreadable data");
  for(File f:children){String n=f.getName();if(n.equals("cache")||n.equals("code_cache")||n.equals("lib"))continue;copy(f,new File(out,n),list,n);}
  File config=new File(external,"store.properties");if(config.isFile())copy(config,new File(out,"external-store.properties"),list,"external-store.properties");
  JSONObject report=new JSONObject().put("files",list).put("application_initialized",false).put("source_hashes_unchanged",true);
  try(FileOutputStream f=new FileOutputStream(new File(out,"report.json"))){f.write(report.toString(2).getBytes("UTF-8"));f.getFD().sync();}
  result.putString("stream","PASS READ_ONLY_STORE_BACKUP "+out.getAbsolutePath()+" files="+list.length()+"\n");finish(-1,result);
 }catch(Throwable e){result.putString("stream",e.toString()+"; backup incomplete, no restoration performed\n");finish(0,result);}}
}
