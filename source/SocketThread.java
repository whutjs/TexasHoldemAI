import java.io.DataInputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
interface MsgCallback{
	public void receiveMsg(String type, String body);
}
class SocketThread extends Thread{
	private Socket socket;
	private MsgCallback callBack;
	private PrintStream ps;
	private DataInputStream inputStream;
	private String serverIp, localIp, ID, name;
	private int serverPort, localPort;
	
	public SocketThread(String sIp, int sPort, String lIp,
			int lPort, String id) {
		serverIp = sIp;
		serverPort = sPort;
		localIp = lIp;
		localPort = lPort;
		ID = id;
		name = "PokerFace";
	}
	
	
	public void setCallBack(MsgCallback cb) {
		callBack = cb;
	}
	
	public void sendMsg(String msg) {
		ps.print(msg);	 	
	}
	@Override
	public void run() {
		SocketAddress localAdrress = new InetSocketAddress(localIp, localPort);
		SocketAddress serverAdrress = new InetSocketAddress(serverIp, serverPort);
		socket = new Socket();
		boolean connect = false;
		do{
			try{
				SocketThread.sleep(500);
				socket.setReuseAddress(true);
				socket.bind(localAdrress);
				socket.connect(serverAdrress);
				socket.setTcpNoDelay(true);
				ps =new PrintStream (socket.getOutputStream());
				ps.print("reg:"+ID+" "+name + " need_notify");	 		
				inputStream = new DataInputStream(socket.getInputStream());
				connect = true;
			}catch(Exception ex){
				Logger.Log(ex.getMessage());
				ex.printStackTrace();
				connect = false;
			}
		}while(connect == false);
		
		Logger.Log("thread run");
		byte[] msg = new byte[6144];
		while(true) {
			try {
				int num = inputStream.read(msg, 0, msg.length);
				if(num == -1) {
					break;
				}
				String message = new String(msg, 0, num, "utf-8").trim();
				Logger.Log("server msg:"+message);
				// 拆包
				if(message.contains("/")) {
					String regex = "(\\w+-?\\w+)(/)";
					Pattern pattern = Pattern.compile(regex, Pattern.DOTALL);
					Matcher m = pattern.matcher(message);
					int lastidx = 0;
					while(m.find()) {
						String type = m.group(1);
						if(type.trim().equals("common")) {
							continue;
						}
						int typeLength = type.length();
						int startidx = message.indexOf(type, lastidx);
						int endidx = message.indexOf(type, startidx + typeLength);
						lastidx = endidx + typeLength;
						String body = message.substring(startidx+typeLength+1,
								endidx-1);
						callBack.receiveMsg(type, body);
					}
				}else if(message.trim().equals("game-over")) {
					Logger.Log("game-over");
					callBack.receiveMsg("game-over", "game-over");
					break;
				}
				if(!message.equals("")){															
					
				}
			} catch (IOException e) {
				e.printStackTrace();
				Logger.Log(e.getMessage());
				//break;
			}
		}
		exit();
	}
	
	private void exit() {
		if(socket != null) {
			try {
				socket.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
}
