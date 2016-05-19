import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
class Logger{
	// 默认关闭
	public static boolean flag = false;
	public synchronized static void Log(String msg) {
		if(!flag) return;
		 try {
			 File outFile = new File("jensonlog.txt");
			 if(!outFile.exists()){
		         outFile.createNewFile();
		      }
			 OutputStreamWriter ow=new OutputStreamWriter(new FileOutputStream(outFile, true));
		     ow.write(msg+"\n"+"-------------------\n");
		     ow.flush();
		     ow.close();
         } catch (FileNotFoundException e){
             e.printStackTrace();
         } catch (IOException e) {
			e.printStackTrace();
		}
		 
	}
}