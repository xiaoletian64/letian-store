package test;
import android.app.Instrumentation;
import android.content.*;
import android.content.pm.*;
import android.os.Bundle;
import java.io.File;
import java.lang.reflect.*;
import java.util.concurrent.atomic.AtomicReference;

/** Session acceptance on owned ranchu only; never installs into user data on a real device. */
public final class InstallerProbe extends Instrumentation {
 private Bundle args;
 public void onCreate(Bundle b){args=b;start();}
 private void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 public void onStart(){Bundle result=new Bundle();try{
  Context c=getTargetContext();check(c.getPackageName().equals("site.zhangyue.store")&&android.os.Build.HARDWARE.equals("ranchu"),"owned store emulator required");
  startActivitySync(new Intent().setClassName(c.getPackageName(),"site.zhangyue.store.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
  ClassLoader l=c.getClassLoader();Class<?> model=l.loadClass("site.zhangyue.store.Model$App"),installer=l.loadClass("site.zhangyue.store.Installer"),callback=l.loadClass("site.zhangyue.store.Installer$Result"),apps=l.loadClass("site.zhangyue.store.Apps");
  String requested=args.getString("mode");
  if("verifySelf".equals(requested)){
   android.content.SharedPreferences p=c.getSharedPreferences("install-sessions",0);int id=p.getInt("last-session",-1);long until=System.currentTimeMillis()+15000;
   while(!"installed".equals(p.getString(id+":state",""))&&System.currentTimeMillis()<until)Thread.sleep(200);
   check(c.getPackageManager().getPackageInfo(c.getPackageName(),0).getLongVersionCode()==11,"self version missing");
   check("installed".equals(p.getString(id+":state","")),"self-update receiver result not preserved");
   check(c.getPackageName().equals(p.getString(id+":package",""))&&p.getLong(id+":version",0)==11,"result belongs to another install");
   check("store8-preserved".equals(c.getSharedPreferences("qa-upgrade",0).getString("marker","")),"self-update data lost");
   result.putString("stream","PASS real self-session update and durable result/old data preserved after probe restart\n");finish(-1,result);return;
  }
  if("self".equals(requested)){
   check(c.getPackageManager().getPackageInfo(c.getPackageName(),0).getLongVersionCode()==10,"self test requires private version10 baseline");
   Object self=model.newInstance();File next=new File(c.getExternalFilesDir("apks"),"self11.apk");model.getField("pkg").set(self,c.getPackageName());model.getField("versionCode").setLong(self,11);model.getField("bytes").setLong(self,next.length());model.getField("sha256").set(self,apps.getMethod("sha256",File.class).invoke(null,next));
   AtomicReference<Boolean> selfOutcome=new AtomicReference<>();
   Object cb=Proxy.newProxyInstance(l,new Class<?>[]{callback},(p,m,a)->{if(m.getName().equals("onResult"))selfOutcome.set((Boolean)a[0]);return null;});
   installer.getMethod("install",Context.class,model,File.class,callback).invoke(null,c,self,next,cb);sendStatus(1,new Bundle());
   long end=System.currentTimeMillis()+120000;
   while(System.currentTimeMillis()<end){
    android.content.SharedPreferences p=c.getSharedPreferences("install-sessions",0);int id=p.getInt("last-session",-1);
    if(c.getPackageManager().getPackageInfo(c.getPackageName(),0).getLongVersionCode()==11&&"installed".equals(p.getString(id+":state",""))){result.putString("stream","PASS system self-update completed; verifySelf must also read after restart\n");finish(-1,result);return;}
    if(Boolean.FALSE.equals(selfOutcome.get()))throw new AssertionError("system self-update failed");
    Thread.sleep(200);
   }
   throw new AssertionError("self-update not completed");
  }
  Object app=model.newInstance();String pkg="site.zhangyue.store.qa.fixture";model.getField("pkg").set(app,pkg);model.getField("versionCode").setLong(app,3);
  File file=new File(c.getExternalFilesDir("apks"),"fixture3.apk");model.getField("bytes").setLong(app,file.length());model.getField("sha256").set(app,apps.getMethod("sha256",File.class).invoke(null,file));
  Method validate=installer.getDeclaredMethod("validate",Context.class,model,File.class);validate.setAccessible(true);String mode=args.getString("mode");
  if("validation".equals(mode)){
   validate.invoke(null,c,app,file);
   model.getField("pkg").set(app,"site.zhangyue.store.qa.wrong");reject(validate,c,app,file);model.getField("pkg").set(app,pkg);
   model.getField("versionCode").setLong(app,4);reject(validate,c,app,file);model.getField("versionCode").setLong(app,3);
   model.getField("bytes").setLong(app,file.length()+1);reject(validate,c,app,file);model.getField("bytes").setLong(app,file.length());
   File wrong=new File(c.getExternalFilesDir("apks"),"fixture4.apk");model.getField("versionCode").setLong(app,4);model.getField("bytes").setLong(app,wrong.length());model.getField("sha256").set(app,apps.getMethod("sha256",File.class).invoke(null,wrong));reject(validate,c,app,wrong);
   result.putString("stream","PASS valid archive; wrong package/version/size/signature rejected before install\n");finish(-1,result);return;
  }
  check(c.getPackageManager().canRequestPackageInstalls(),"allow this test source through normal system settings first");
  if("tamper".equals(mode))model.getField("sha256").set(app,"0000000000000000000000000000000000000000000000000000000000000000");
  AtomicReference<Boolean> outcome=new AtomicReference<>();
  Object cb=Proxy.newProxyInstance(l,new Class<?>[]{callback},(p,m,a)->{if(m.getName().equals("onResult"))outcome.set((Boolean)a[0]);return null;});
  installer.getMethod("install",Context.class,model,File.class,callback).invoke(null,c,app,file,cb);sendStatus(1,new Bundle());
  long until=System.currentTimeMillis()+120000;while(outcome.get()==null&&System.currentTimeMillis()<until)Thread.sleep(200);
  check(outcome.get()!=null,"system install did not return result");boolean success="success".equals(mode);check(outcome.get()==success,"wrong terminal success result");
  long expected=success?3:2;check(c.getPackageManager().getPackageInfo(pkg,0).getLongVersionCode()==expected,"fixture version changed incorrectly");
  if(!"tamper".equals(mode)){
   android.content.SharedPreferences state=c.getSharedPreferences("install-sessions",0);int id=state.getInt("last-session",-1);check(id>=0,"no saved session");check((success?"installed":"failed").equals(state.getString(id+":state","")),"wrong durable terminal result");
  }
  result.putString("stream","PASS installer "+mode+" with actual installed version/result checked\n");finish(-1,result);
 }catch(Throwable e){result.putString("stream",e.toString()+"\n");finish(0,result);}}
 private void reject(Method method,Context c,Object app,File file)throws Exception{
  try{method.invoke(null,c,app,file);throw new AssertionError("bad candidate accepted");}
  catch(InvocationTargetException expected){check(expected.getCause() instanceof SecurityException,"unexpected validation error");}
 }
}
