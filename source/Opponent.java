import java.util.HashMap;



class Opponent {
	
	/* 使用weight array建模 */
	// bets-to-call index(当做是raiseCnt)
	public static final int BETS_TO_CALL_NUM = 3;
	public static final int NOBET_TOCALL = 0;			// 没人加注
	public static final int ONEBET_TOCALL = 1;			// 一个人加注
	public static final int TWOPLUS_TOCALL = 2;			// 两个及以上
	// 当前是什么轮
	public static final int ROUND_NUM = 4;
	public static final int PREFLOP_ROUND = 0;		
	public static final int FLOP_ROUND = 1;	
	public static final int TURN_ROUND = 2;	
	public static final int RIVER_ROUND = 3;	
	// 玩家动作
	public static final int ACTION_NUM = 3;
	public static final int FOLD = 0;		
	public static final int CHECK_CALL = 1;	
	public static final int RAISE_ALLIN = 2;
	// 加注金额
	public static final int RAISE_NUM = 5;	
	// 表示要求的是全部加注的数量
	public static final int ALL_RAISE = 4;
	public static final int RAISE0_200 = 0;	// 加注金额0~200
	public static final int RAISE200_500 = 1;	 // 加注金额200~500
	public static final int RAISE500_1000 = 2;		// 500~1000以上
	public static final int RAISE1000 = 3;			// 1000以上
	
	// 用来记录次数,第一维是轮数=4，第2维是加注数=3，第3维是动作数=3,第四维是加注额=3；
	private int T[][][][];
	// 表示某个context下，各行为次数的和
	private int sum[][];
	// 频率
	public float frequency[][][];
	/**
	 * 权重表, key=一对手牌的值。每一局都要清空数据，使用时 ，如果不含key，则value=1
	 */
//	public HashMap<Long, Float> weight = new HashMap<Long, Float>(2800, 0.5f);
	public float[] weight = new float[1326];
	
	public Opponent() {
		T = new int[ROUND_NUM][BETS_TO_CALL_NUM][ACTION_NUM][RAISE_NUM];
		sum = new int[ROUND_NUM][BETS_TO_CALL_NUM];
		frequency = new float[ROUND_NUM][BETS_TO_CALL_NUM][ACTION_NUM];
	}
	
	private int getRoundIdx(State round) {
		if(round == State.PRE_FLOP) {
			return PREFLOP_ROUND;
		}else if(round == State.FLOP) {
			return FLOP_ROUND;
		}else if(round == State.TURN) {
			return TURN_ROUND;
		}else{
			return RIVER_ROUND;
		}
	}
	
	private int getBetsToCallIdx(int raiseNum) {
		int idx = 0;
		if(raiseNum == 0) {
			idx = NOBET_TOCALL;
		}else if(raiseNum == 1) {
			idx = ONEBET_TOCALL;
		}else if(raiseNum > 1) {
			idx = TWOPLUS_TOCALL;
		}
		return idx;
	}
	
	private int getActionIdx(String action) {
		int a_idx = 0; 
		if(action.equals("call") || action.equals("check")) {
			a_idx = CHECK_CALL;
		}else if(action.equals("all_in") || action.contains("raise"))
		{
			a_idx = RAISE_ALLIN;
		}else if(action.equals("fold")) {
			a_idx = FOLD;
		}
		return a_idx;
	}
	/**
	 * 
	 * @param round
	 * @param raiseNum 跟住的数目
	 * @param action
	 * @param raiseAmount 加注的金额
	 */
	public void handleAction(State round, int raiseNum, String action, int raiseAmount) {
		int r_idx = getRoundIdx(round);
		int b_idx = getBetsToCallIdx(raiseNum);
		int a_idx = getActionIdx(action);
		int raise_idx = RAISE0_200;
		if(raiseAmount >= 1000) {
			raise_idx = RAISE1000;
		}else if(raiseAmount >= 500){
			raise_idx = RAISE1000;
		}else
		if(raiseAmount >= 200) {
			raise_idx = RAISE200_500;
		}else if(raiseAmount == -1) {
			// 表示要统计的是所有的加注
			raise_idx = ALL_RAISE;
		}
		if(a_idx == RAISE_ALLIN) {
			Logger.Log(ID+"in handleAction raiseAmount="+raiseAmount+" raiseIdx="+raise_idx);
		}
		T[r_idx][b_idx][a_idx][raise_idx]++;
		// 在记录的时候就统计
		T[r_idx][b_idx][a_idx][ALL_RAISE]++;
		if(raiseAmount >= 1000) {
			Logger.Log(ID+"RAISE>1000 count="+T[r_idx][b_idx][a_idx][raise_idx]);
		}
	}
	
	
	/**
	 * 递归调用来计算频率
	 * @param r_idx
	 * @param b_idx
	 * @param a_idx
	 * @param raise_idx
	 * @return
	 */
	public final double getFrequencyRecur(final int r_idx, final int b_idx, 
			final int a_idx , final int raise_idx)
	{
		if(r_idx < 0) {
			return OpponentModel.d[b_idx][a_idx];
		}
		sum[r_idx][b_idx] = 0;
		for(int i = 0; i < ACTION_NUM; i++) {
			for(int j = 0; j < RAISE_NUM-1; j++) {
				sum[r_idx][b_idx] += T[r_idx][b_idx][i][j];
			}
		}
		if(sum[r_idx][b_idx] >= 20) {
			return (T[r_idx][b_idx][a_idx][raise_idx] / (double)sum[r_idx][b_idx]);
		}
		double f = (T[r_idx][b_idx][a_idx][raise_idx] / (double)sum[r_idx][b_idx]);
		double preF = getFrequencyRecur(r_idx-1, b_idx, a_idx, raise_idx);
		double res = f*(sum[r_idx][b_idx]/(double)20) + preF*((20 - sum[r_idx][b_idx])/(double)20);
		return res;
	}
	
	/**
	 * 获得特定context下，某动作的频率
	 * @param round
	 * @param raiseNum
	 * @param action
	 * @param raiseAmount 加注的金额，如果不是raise,amount=0就好
	 * @return 
	 */
	public final double getFrequency(final State round, final int raiseNum, 
			final String action, final int raiseAmount) {
//		Logger.Log("in getFrequency:");
//		Logger.Log("round:"+round+" raiseNum:"+raiseNum+" action:"+action);
		double res = 0;
		int r_idx = getRoundIdx(round);
		int b_idx = getBetsToCallIdx(raiseNum);
		int a_idx = getActionIdx(action);
		int raise_idx = RAISE0_200;
		if(raiseAmount >= 1000) {
			raise_idx = RAISE1000;
		}else if(raiseAmount >= 500){
			raise_idx = RAISE1000;
		}else if(raiseAmount >= 200) {
			raise_idx = RAISE200_500;
		}else if(raiseAmount == -1) {
			raise_idx = ALL_RAISE;
		}
		// 使用递归计算，看算得准不准
		res = getFrequencyRecur(r_idx, b_idx, a_idx, raise_idx);
		return res;
//		float d = (float) OpponentModel.d[b_idx][a_idx];
////		Logger.Log("r_idx:"+r_idx+" b_idx:"+b_idx+" a_idx:"+a_idx+" d="+d);
//		// 计算在该上下文中，该行为的总和
//		sum[r_idx][b_idx] = 0;
//		for(int i = 0; i < ACTION_NUM; i++) {
//			for(int j = 0; j < RAISE_NUM; j++) {
//				sum[r_idx][b_idx] += T[r_idx][b_idx][i][j];
//			}
//		}
//		if(raise_idx >= 2) {
//			Logger.Log("raise>500时，sum[r_idx][b_idx]:"+sum[r_idx][b_idx]);
//		}
//		// 计算频率
//		frequency[r_idx][b_idx][a_idx] = T[r_idx][b_idx][a_idx][raise_idx] / (float)sum[r_idx][b_idx];
//		Logger.Log("T[r_idx][b_idx][a_idx]["+raise_idx+"]:"+T[r_idx][b_idx][a_idx][raise_idx]+" frequency[r_idx][b_idx][a_idx]:"+frequency[r_idx][b_idx][a_idx]);
//		if(a_idx != RAISE_ALLIN && sum[r_idx][b_idx] < 20) {
//			// 如果有加注，不能用默认的，不然会降低精度
//			// 数据太少，用默认的
//			float arg1 = frequency[r_idx][b_idx][a_idx]*(sum[r_idx][b_idx]/20f);
//			float arg2 = (d * ((20 - sum[r_idx][b_idx])/20f));
//			res = arg1 + arg2;
//		}else{
//			res = frequency[r_idx][b_idx][a_idx];
//		}
//		res = frequency[r_idx][b_idx][a_idx];
////		Logger.Log("res:"+res);
		
	}
	
	public String ID = "";
	
	/* 
	 * 用来在inquire或者notify的时候区分上下家，重要！
	 * true:表示是我的下家,在我之后行动。我也默认是下家
	 * false:是我的上家
	*/ 
	boolean isBehindMe = false;
	public int money = 2000;
	public int jetton = 2000;
	// 位置
	public int pos = -1;
	// 上次行动
	public String lastAction = "";
	// 当前行动
	public String curAction = "";
	// 做出当前行动时的上下文
	public State round_Context;
	public int betToCall_Context;
	
	// 之前下注额，当前下注额
	public int preBet = 0, curBet = 0;
	private boolean isTight = false;
	// 平均加注额
	public float averageRaise = 0;
	// 各行动总共的次数
	public int foldCnt = 0, callCnt = 0,
			checkCnt = 0, raiseCnt = 0,
			allinCnt = 0,
			// 在flop前弃牌
			foldBeforeFlop = 0,
			// 翻牌前就加注，多半是bluff
			raisePreFlop = 0;
	@Override
	public String toString() {
		float af = (raiseCnt + allinCnt) / (float)(callCnt + checkCnt);
		float callRate = (float)callCnt / (raiseCnt + allinCnt + foldCnt);
		String str = "ID:"+ID				
				+" isBehindMe:"+isBehindMe
				+"\nT[PREFLOP_ROUND][NOBET_TOCALL][CHECK_CALL][RAISE0_200]:"+T[PREFLOP_ROUND][NOBET_TOCALL][CHECK_CALL][RAISE0_200]
				+"\nT[FLOP_ROUND][NOBET_TOCALL][CHECK_CALL]:"+T[FLOP_ROUND][NOBET_TOCALL][CHECK_CALL][RAISE0_200]
				+"\nT[TURN_ROUND][NOBET_TOCALL][CHECK_CALL]:"+T[TURN_ROUND][NOBET_TOCALL][CHECK_CALL][RAISE0_200]
			    +"\nT[RIVER_ROUND][NOBET_TOCALL][CHECK_CALL]:"+T[RIVER_ROUND][NOBET_TOCALL][CHECK_CALL][RAISE0_200]
				+"\nT[PREFLOP_ROUND][ONEBET_TOCALL][CHECK_CALL]:"+T[PREFLOP_ROUND][ONEBET_TOCALL][CHECK_CALL][RAISE0_200]
				+"\nT[FLOP_ROUND][ONEBET_TOCALL][CHECK_CALL]:"+T[FLOP_ROUND][ONEBET_TOCALL][CHECK_CALL][RAISE0_200]
				+"\nT[TURN_ROUND][ONEBET_TOCALL][CHECK_CALL]:"+T[TURN_ROUND][ONEBET_TOCALL][CHECK_CALL][RAISE0_200]
				+"\nT[RIVER_ROUND][ONEBET_TOCALL][CHECK_CALL]:"+T[RIVER_ROUND][ONEBET_TOCALL][CHECK_CALL][RAISE0_200]
				+"\nT[PREFLOP_ROUND][NOBET_TOCALL][RAISE_ALLIN]:"+T[PREFLOP_ROUND][NOBET_TOCALL][RAISE_ALLIN][RAISE0_200]
				+"\nT[FLOP_ROUND][NOBET_TOCALL][RAISE_ALLIN]:"+T[FLOP_ROUND][NOBET_TOCALL][RAISE_ALLIN][RAISE0_200]
				+"\nT[TURN_ROUND][NOBET_TOCALL][RAISE_ALLIN]:"+T[TURN_ROUND][NOBET_TOCALL][RAISE_ALLIN][RAISE0_200]
				+"\nT[RIVER_ROUND][NOBET_TOCALL][RAISE_ALLIN]:"+T[RIVER_ROUND][NOBET_TOCALL][RAISE_ALLIN][RAISE0_200];
		return str;
	}
	/**
	 * 是否是tight玩家，false=loose, true=tight
	 * @param round 当前已经玩了多少局
	 * @return
	 */
	public boolean isTight(int round) {
		// 跟注到flop的比例
		float vpip = (round - foldBeforeFlop) / (float)round;
		Logger.Log("foldBeforeFlop="+foldBeforeFlop+" round="+round+" vpip="+vpip);
		if(vpip <= 0.4) {
			// tight(40%)都弃牌
			isTight = true;
			return true;
		}else{
			isTight = false;
			return false;
		}
	}
	
	/**
	 * 是否是被动型玩家
	 * @return
	 */
	public boolean isPassive() {
		float af = (raiseCnt + allinCnt) / (float)(callCnt + checkCnt);
		if(af >= 1) {
			return false;
		}
		return true;
	}
}