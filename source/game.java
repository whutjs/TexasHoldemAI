import java.io.*;
import java.net.*;
import java.util.regex.*;
//class Card{		
//	public int rank;	// 点数
//	public int suit;	// 花色
//}
public class game {
	public static void main(String[] args) {
		if(args.length< 5){
			System.out.println("lack of args!");
			return;
		}
		String flag = args[5];
		Logger.Log("flag="+flag);
		if(flag != null && !flag.equals("")) {
			// 开关调试模式
			try{
				boolean t = Boolean.parseBoolean(flag);
				Logger.Log("flag="+flag+" t="+t);
				Logger.flag = t;
			}catch(Exception ex) {
				Logger.Log(ex.getMessage());
			}
		}
		
		Player player = new Player(args[0], Integer.parseInt(args[1]), args[2], 
						Integer.parseInt(args[3]), args[4]);
		try{
			player.play();
		}catch(Exception ex) {
			Logger.Log(ex.getMessage());
		}
	}
}
