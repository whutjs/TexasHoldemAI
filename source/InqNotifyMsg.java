
/**
 * 表示在Inquire或者Notify消息里面的各玩家
 * @author jenson
 *
 */
class InqNotifyMsg {	
	public InqNotifyMsg() {
		
	}
	public InqNotifyMsg(String id, boolean isBehind, String act, 
			String preAct, int preB, int curB) {
		this.ID = id;
		this.isBehindMe = isBehind;
		this.action = act;
		this.preAction = preAct;
		this.preBet = preB;
		this.curBet = curB;
	}
	String ID = "";
	/* 
	 * true:表示是我的下家,在我之后行动。我也默认是下家
	 * false:是我的上家
	*/ 
	boolean isBehindMe = false;
	
	String action = "";
	
	String preAction = "";
	int preBet = 0;
	int curBet = 0;
	@Override
	public String toString() {
		return "ID="+ID+" preAction="+preAction+" action="+action+
				" preBet="+preBet+" curBet="+curBet;
	}

}
