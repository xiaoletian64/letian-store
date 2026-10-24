package test;
import android.app.Instrumentation;
import android.content.Context;
import android.os.Bundle;
import java.lang.reflect.*;
/** Reads state only. Run against an owned emulator with the fixture packages. */
public final class InstalledStateProbe extends Instrumentation {
 private Bundle args;
 public void onCreate(Bundle b){args=b;start();}
 private void check(boolean value,String message){if(!value)throw new AssertionError(message);}
 public void onStart(){Bundle result=new Bundle();try{
  Context c=getTargetContext();check(c.getPackageName().equals("site.zhangyue.store"),"store only");
  String mode=args.getString("mode", "matrix");
  if(mode.equals("seedMarker")||mode.equals("verifyMarker")){
   check(android.os.Build.HARDWARE.equals("ranchu"),"owned emulator only");
   android.content.SharedPreferences p=c.getSharedPreferences("qa-upgrade",0);
   if(mode.equals("seedMarker"))check(p.edit().putString("marker","store8-preserved").commit(),"fixture commit failed");
   check("store8-preserved".equals(p.getString("marker","")),"upgrade marker lost");
   result.putString("stream","PASS store upgrade marker "+mode+"\n");finish(-1,result);return;
  }
  ClassLoader l=c.getClassLoader();Class<?> apps=l.loadClass("site.zhangyue.store.Apps"),model=l.loadClass("site.zhangyue.store.Model$App");
  Object app=model.newInstance();Field pkg=model.getField("pkg"),vc=model.getField("versionCode");
  Method status=apps.getMethod("status",Context.class,model),version=apps.getMethod("installedVersion",Context.class,model);
  pkg.set(app,"site.zhangyue.minis");long installed=c.getPackageManager().getPackageInfo("site.zhangyue.minis",0).getLongVersionCode();
  check(installed==121,"known formal121 required");
  vc.setLong(app,121);check((Integer)status.invoke(null,c,app)==2,"current package invisible");
  vc.setLong(app,122);check((Integer)status.invoke(null,c,app)==1,"older package not updatable");
  vc.setLong(app,120);check((Integer)status.invoke(null,c,app)==2,"newer install offered downgrade");
  check((Long)version.invoke(null,c,app)==121,"wrong installed version");
  pkg.set(app,"site.zhangyue.store.qa.absent");check((Integer)status.invoke(null,c,app)==0,"absent package marked installed");
  if("transition".equals(args.getString("mode"))){
   pkg.set(app,"site.zhangyue.store.qa.fixture");vc.setLong(app,2);
   check((Integer)status.invoke(null,c,app)==1,"fixture1 must be older");
   sendStatus(1,new Bundle());long until=System.currentTimeMillis()+90000;
   while(System.currentTimeMillis()<until&&(Integer)status.invoke(null,c,app)!=2)Thread.sleep(500);
   check((Integer)status.invoke(null,c,app)==2,"installation state stale in same process");
  }
  result.putString("stream","PASS absent/current/older/newer and installed version; "+args.getString("mode", "matrix")+"\n");finish(-1,result);
 }catch(Throwable error){result.putString("stream",error.toString()+"\n");finish(0,result);}}
}
