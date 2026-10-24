package test;
import android.app.Instrumentation;
import android.content.*;
import android.os.Bundle;
import java.lang.reflect.*;
import org.json.*;
/** Injects a synthetic catalog in memory, then opens the real old8 detail screen. No app files changed. */
public final class LegacyLinkProbe extends Instrumentation {
 private Bundle args;
 public void onCreate(Bundle b){args=b;start();}
 public void onStart(){Bundle out=new Bundle();try{
  Context c=getTargetContext();if(!c.getPackageName().equals("site.zhangyue.store")||!android.os.Build.HARDWARE.equals("ranchu")||c.getPackageManager().getPackageInfo(c.getPackageName(),0).versionCode!=8)throw new SecurityException("owned old8 only");
  String url="http://127.0.0.1:18789/index.html";
  JSONObject app=new JSONObject().put("slug","letian-store").put("name","商店兼容升级 · 隔离验收").put("package",c.getPackageName()).put("versionCode",10).put("versionName","3.0.2").put("url","http://127.0.0.1:18789/store.apk").put("web",url).put("notes","本轮合成目录，只使用既有网页版入口验证系统覆盖，不改稳定目录。");
  ClassLoader l=c.getClassLoader();Object catalog=l.loadClass("site.zhangyue.store.Model").getMethod("parse",String.class).invoke(null,new JSONObject().put("apps",new JSONArray().put(app)).toString());Class<?> repo=l.loadClass("site.zhangyue.store.Repo");Field f=repo.getDeclaredField("sCatalog");f.setAccessible(true);f.set(null,catalog);repo.getField("lastFetchAt").setLong(null,System.currentTimeMillis());
  Intent intent=new Intent().setClassName(c.getPackageName(),"site.zhangyue.store.DetailActivity").putExtra("slug","letian-store").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);startActivitySync(intent);
  out.putString("stream","READY actual old8 web-link screen, memory-only fixture; waiting for system upgrade\n");sendStatus(1,out);for(int i=0;i<600;i++)Thread.sleep(1000);finish(-1,out);
 }catch(Throwable e){out.putString("stream",e.toString()+"\n");finish(0,out);}}
}
