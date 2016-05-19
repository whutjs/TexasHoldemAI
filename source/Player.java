import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.ListIterator;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;







enum State{
	//翻牌前状态    翻牌         转牌       和牌
	PRE_FLOP, FLOP, TURN, RIVER
}
class Position{
	 public static final int EARLY_POS = 0,   // 靠前位置
			    MID_POS = 1,      // 中间位置
			    LATE_POS = 2,      //靠后位置
			    SBLIND_POS = 3, //小盲注位置
			    BLIND_POS = 4;   //大盲注位置
}
// 游戏风格
enum GameStyle{
	// 宽松的
	LOOSE_GAME,
	MODERATE_GAME,
	// 大家都很保守
	TIGHT_GAME;
}
class Player implements MsgCallback{
	
//	ThreadPoolExecutor threadPool = 
//			new ThreadPoolExecutor(4, 16, 10, TimeUnit.MINUTES, new LinkedBlockingQueue<Runnable>());
	
	private final int ALL_ROUND_NUM = 600;
	// 保存对手信息，key=ID
	private HashMap<String, Opponent> opponents = new HashMap<String, Opponent>(11);
	// 当前轮没有弃牌的对手
	private HashSet<String> aliveOpp = new HashSet<String>(11);
	// 用来保存inquire或者Notify消息里面当前玩家的顺序
	private LinkedList<InqNotifyMsg> curMsgList = new LinkedList<InqNotifyMsg>();
	// 保存上一圈（轮）的msg消息
	private LinkedList<InqNotifyMsg> preMsgList = new LinkedList<InqNotifyMsg>();
	// 表示我当前的行为和赌注
	private String myLastAction = "", myCurAction = "";
	private int myPreBet = 0;
	
	// 表示当前是该round的第几圈，对于统计信息有用.
	private int circle = 0;
	
	// 我的游戏风格，一开始要稳健
	private GameStyle myGameStyle = GameStyle.LOOSE_GAME;
	/* 使用base-incre 决策时，默认的probability_play
	 * probability_play：在preflop不fold的玩家的百分比
	 */
	private final double PLAY_ODDS_DEFAULT = 0.6f;
	// 上次计算的值
	private double lastCacPlayOdds = PLAY_ODDS_DEFAULT;
	private double probability_play[] = new double[600];
	private int probPlayIdx = 0;
	
	private int foldPreFlop = 0;
	// 在inquire消息里面，出现了多少个player
	private int playerCntInInquire = 0;
	
	// 筹码保护时的handStrength
	private final double JettonProtectHS = 0.70f;
	private final double HANDSTRENGTH_REDUCE = 0.15f;
	
	private final double STRENGTH_DRAW_INCREASE = 0.50f;
	/* 打法是不是紧的，false=松，大胆； true=紧，谨慎
	 * 如果金钱排名>=4,则采用紧的打法，否则采用松的打法
	 */
	private boolean isTight = true;
	// 还有多少局剩余
	private int remainTurn = ALL_ROUND_NUM;
	private int lastRound = 1;	// 之前是第几局
	// 牌值评估程序
	private KRHandEvaluator handEval;
	
	// 当前到了哪个阶段
	private State currentState = State.PRE_FLOP;
	// 统计轮到我行动时前面选手的行动
	private int allinCnt, foldCnt, callCnt, raiseCnt, checkCnt, blindCnt;
	// 如果要跟住，需要下的最小注
	private int minBet;
	// 当前已经下注的钱
	private int myCurBet;
	private int bigBlindBet;
	// 我所有的钱和筹码
	private int myAllMoney = 2000, myAllJetton = 2000;
	// 表示我总共的钱-敌人总共的钱
	private int moneyGap;
	
	// 是否是顺子听筒（差一张顺子）
	private boolean isStraightDraw = false;
	// 是否是同花听筒
	private boolean isFlushDraw = false;
	
	// 当前位置
	private int myCurPos;
	// 与庄家的距离，SB=1， BB=2;
	private int awayFromBtn;
	// 记录自己每一轮加注了多少次
	private int raisePreFlopCnt = 0, raiseFLopCnt = 0, raiseTurnCnt = 0, raiseRiverCnt = 0;
	
	// 至少要加注多少
	private int leastRaise = 0;
	// semi-bluff ,turn和river用到
	private boolean semiBluffFlag = false;
	
	// 当前底池总金额（本轮投注已累计）
	private int totalPot;
	
	// 两张底牌
	private int rank1, suit1, rank2, suit2;
	// 公共牌
	private int shareRank[] = new int[6];
	private int shareSuit[] = new int[6];
	// 在使用KRHandEval时用到
	private int boardCardVal[] = new int[6];
	private static int shareIdx; 	// 下标
		    		
	private final String CALL = "call";		// 跟住
	private final String CHECK = "check";		// 让牌
	private final String RAISE = "raise";		// 加注
	private final String ALL_IN = "all_in";		// 全压
	private final String FOLD = "fold";			// 弃牌
	
	private final String BLIND = "blind";			// 盲注
	
	private final String DIAMONDS = "DIAMONDS";		// 方块
	private final String SPADES = "SPADES";		// 黑桃
	private final String CLUBS = "CLUBS";		// 梅花
	private final String HEARTS = "HEARTS";		// 红桃
	
	private String serverIp, localIp, ID, name;
	private int serverPort, localPort;
	
	private SocketThread thread;
	
	// 上次记录时，有多少个选手,用于每减少2个人，重置一次foldpreflop统计
	private int preAllPlayerNums = 0;
	// 这场比赛有多少个选手
	private int allPlayerNums = 0;
	// 当前这轮还有多少个选手（除去Fold的）
	private int curHandPlayerNums = 0;
	
	
	public Player() {
		name = "PokerFace";
	}
	public Player(String sIp, int sPort, String lIp,
			int lPort, String id) {
		serverIp = sIp;
		serverPort = sPort;
		localIp = lIp;
		localPort = lPort;
		ID = id;
		name = "PokerFace";
	}
	/**
	 * 根据opponents保存的信息获取游戏风格（至少要玩40局才知道）
	 * @return
	 */
	private GameStyle getGameStyle() {
		int round = ALL_ROUND_NUM - remainTurn;
		if(round < 40) {
			// 才玩40局不到
			return GameStyle.TIGHT_GAME;
		}
		int players = opponents.size();
		int tightCnt = 0;
		for(Opponent opp : opponents.values()) {
			Logger.Log("opp:"+opp);
			if(opp.isTight(round)) {
				tightCnt++;
			}
		}
		double p = (double)tightCnt / players;
		if(p >= 0.7f) {
			// 70%都是tight，也就是7个对手至少有5个是tight
			return GameStyle.TIGHT_GAME;
		}else{
			return GameStyle.LOOSE_GAME;
		}
	}
	
	// 用来判断是否是顺子听筒
	private int pokerCnt[] = new int[15];
	// 公牌是否是听筒状态
	private boolean isBoardStraightDraw() {
		for(int i = 0; i < 15; i++) pokerCnt[i] = 0;
		for(int i = 0; i < shareIdx; i++) {
			pokerCnt[shareRank[i]]++;
		}
		boolean flag = false;
		if(pokerCnt[Poker.A] == 1 && pokerCnt[2] == 1
		&& pokerCnt[3] == 1 && pokerCnt[4] == 1	) {
			// 特殊的A,2，3，4，5也是顺子
			return true;
		}
		// 在点数范围判断
		int i = 2, j = i;
		// J以后就没有可能连成顺子了
		while(i < Poker.J && !flag) {
			if(pokerCnt[i] != 0) {
				// i处值不为0，i处开始判断是否有连续4个不为0
				for(j = i+1; j < i+4; j++) {
					if(pokerCnt[j] == 0) {
						// j处为0，i从j的下一个开始判断
						i = j +1;
						break;
					}
				}
				if(j - i == 4){
					// 能走完循环，说明是顺子
					flag = true;
				}
			}else{
				i++;
			}
		}
		return flag;
	}
	// 是否是顺子听筒
	private boolean isStraightDraw() {
		for(int i = 0; i < 15; i++) pokerCnt[i] = 0;
		for(int i = 0; i < shareIdx; i++) {
			pokerCnt[shareRank[i]]++;
		}
		pokerCnt[rank1]++;
		pokerCnt[rank2]++;
		boolean flag = false;
		if(pokerCnt[Poker.A] == 1 && pokerCnt[2] == 1
		&& pokerCnt[3] == 1 && pokerCnt[4] == 1	
		&& pokerCnt[5] == 1	) {
			// 特殊的A,2，3，4，5也是顺子
			return true;
		}
		// 在点数范围判断
		int i = 2, j = i;
		// J以后就没有可能连成顺子了
		while(i < Poker.J && !flag) {
			if(pokerCnt[i] != 0) {
				// i处值不为0，i处开始判断是否有连续4个不为0
				for(j = i+1; j < i+4; j++) {
					if(pokerCnt[j] == 0) {
						// j处为0，i从j的下一个开始判断
						i = j +1;
						break;
					}
				}
				if(j - i == 4){
					// 能走完循环，说明是顺子
					flag = true;
				}
			}else{
				i++;
			}
		}
		return flag;
	}
	// 是否是同花听筒
	private boolean isFlushDraw() {
		boolean flag = false;
		int scnt = 0, hcnt = 0, ccnt = 0, dcnt = 0;
		for(int i = 0; i < shareIdx; i++) {
			switch(shareSuit[i]){
			case Poker.Clubs:
				ccnt++;
				break;
			case Poker.Diamonds:
				dcnt++;
				break;
			case Poker.Hearts:
				hcnt++;
				break;
			case Poker.Spades:
				scnt++;
				break;
			}
		}
		switch(suit1){
			case Poker.Clubs:
				ccnt++;
				break;
			case Poker.Diamonds:
				dcnt++;
				break;
			case Poker.Hearts:
				hcnt++;
				break;
			case Poker.Spades:
				scnt++;
				break;
		}
		switch(suit2){
			case Poker.Clubs:
				ccnt++;
				break;
			case Poker.Diamonds:
				dcnt++;
				break;
			case Poker.Hearts:
				hcnt++;
				break;
			case Poker.Spades:
				scnt++;
				break;
		}
		if(ccnt == 4 || dcnt == 4 || hcnt == 4 || scnt == 4) {
			flag = true;
		}
		return flag;
	}
	
	// 首先交换底牌1和2的点数，保证rank1>rank2
	private void exchangePocket() {
		// 首先交换底牌1和2的点数，保证rank1>rank2
		if(rank2 > rank1) {
			int tmp = rank2;
			rank2 = rank1;
			rank1 = tmp;
			// 交换花色
			tmp = suit2;
			suit2 = suit1;
			suit1 = tmp;
		}
	}
	//volatile double calEhsRes[] = new double[2];
	/**
	 * 开线程计算，如果超时则返回false
	 * @return
	 */
	private double[] calEHS() {
		double res[] = handEval.calEHS();
		double ppot = res[0];
		double strength = res[1];
		if(aliveOpp.size() > 0) {
			strength = Math.pow(strength, aliveOpp.size());
		}		
		double ehs = strength + (1-strength)*ppot;
		res[1] = ehs;
		return res;
//		ExecutorService exs = Executors.newSingleThreadExecutor();
//		exs.execute(new Runnable() {
//			
//			@Override
//			public void run() {
//				calEhsRes = handEval.calEHS();
//			}
//		});
//		exs.shutdown();
//		try {
//			boolean done = exs.awaitTermination(350, TimeUnit.MILLISECONDS);
//			double ppot = calEhsRes[0];
//			double strength = calEhsRes[1];
//			if(aliveOpp.size() > 0) {
//				strength = Math.pow(strength, aliveOpp.size());
//			}		
//			double ehs = strength + (1-strength)*ppot;
//			Logger.Log("strength="+calEhsRes[1]+" afterStrength="+strength+" ppot="+ppot
//					+" ehs="+ehs);
//			calEhsRes[1] = ehs;
//			return done;
//		} catch (InterruptedException e) {
//			Logger.Log("InterruptedException:"+e.getMessage());
//		}
//		return false;
	}
	
	// 当钱已经很多时，适当降低出手率
	private double keepMoneyStrategy(double strength) {
		if(strength >= 0.90) {
			// 够牛逼了 上！
			return strength;
		}
		if (willWin())
	    {
			// 已经会赢啦，你们慢慢打
	        return 0.01f;
	    }
		if (moneyGap > 0 && allPlayerNums > 2)
	    {
			// 钱多，要悠着打
	        strength -= HANDSTRENGTH_REDUCE;
	    }
	    return strength;
	}
	/**
	 * 计算回报率
	 * @return
	 */
	private double calRateOfReturn(double strength) {
		if(isJettonProtect(strength)) {
			// 需要金币保护
			return 0;
		}
	    strength = keepMoneyStrategy(strength);
		double potOdds = (double)(totalPot - myCurBet)/(totalPot + minBet);
	    double RR = strength / potOdds;
	    return RR;
	}
	/**
	 * 返回可组成的最大牌的等级
	 * @return
	 */
	private int getHandRank() {
		return handEval.getMyHandType();
	}
	
	// 根据现有的金钱判断是否一定会赢了
	private boolean willWin() {
		// 如果我的钱比对手所有的加起来还多，那么基本上就赢定啦！
	    if (moneyGap > 0)
	    {
	    	int smallBet = bigBlindBet / 2;
	    	// 如果接下来一直fold会花多少钱
	        int foldMoney = 0;
	        if (allPlayerNums == 2)
	        {
	        	// 只有两个人时一直fold要花多少钱
	            foldMoney = ((remainTurn / 2) + 1) * smallBet;
	        }
	        else
	        {
	            foldMoney = ((remainTurn / allPlayerNums) + 1) * (smallBet + bigBlindBet);
	        }
	        if (moneyGap >= foldMoney * 2)
	        {
	        	// 如果多出的钱比一直fold还多一倍，那就一直fold吧！
	            return true;            
	        }
	    }
	    return false;
	}
	/**
	 * 是否应该筹码保护
	 * @param strength
	 * @return
	 */
	private boolean isJettonProtect(double strength) {
		int remainJetton = myAllJetton;
		// 剩余筹码已经不能进行5次盲注了
		 if ((remainJetton - myCurBet) < (bigBlindBet * 5)
			 && (strength < JettonProtectHS) )
		 {
			 return true;
		 }
			    
	    return false;
	}
	
	// 现在的金钱，是否能撑到最后
	private boolean canLiveTillEnd() {
		int smallBet = bigBlindBet / 2;
    	// 如果接下来一直fold会花多少钱
        int foldMoney = 0;
        if(allPlayerNums == 0) {
        	allPlayerNums = 8;
        }
        if (allPlayerNums == 2)
        {
        	// 只有两个人时一直fold要花多少钱
            foldMoney = ((remainTurn / 2) + 1) * smallBet;
        }
        else
        {
            foldMoney = ((remainTurn / allPlayerNums) + 1) * (smallBet + bigBlindBet);
        }
        Logger.Log("allplayers="+allPlayerNums+" foldMoney:"+foldMoney);
        if(foldMoney > myAllJetton + myAllMoney) {
        	// 撑不了
        	return false;
        }
        return true;
	}
	// 计算阈值
	private int getThreshold(int group, int make, int tightness) {
		int res[] = PreflopData.getBaseIncrement(group, make, tightness);
		int base = res[0], incre = res[1];
		// 表示还有多少个人没做出决策
		int position = allPlayerNums -awayFromBtn;
		return base + incre * position;
	}
	
	/* 
	 * 计算进入flop的玩家的比率
	 * 每10局计算一次吧，不然耗时太大
	 */
	private double getProbabilityPlay() {
		double probabilityPlay = lastCacPlayOdds;
		// 已经进行多少局
		int roundNum = ALL_ROUND_NUM - remainTurn;
		if(probPlayIdx > 0 && roundNum > 1 && roundNum % 10 == 0) {
			double allProb = 0f;
			// 每10局，使用统计下来的play率
			for(int i = 0; i < probPlayIdx; i++) {
				allProb += probability_play[i];
			}
			if(probPlayIdx > 0) {
				probabilityPlay = allProb / probPlayIdx;
			}
			lastCacPlayOdds = probabilityPlay;
		}
		return probabilityPlay;
	}
	// preflop策略2,使用Income Rates表
	private void makePreFlopAction() {
		String action = FOLD;
		if(!handEval.initOK()) {
			// 数据还没初始化完毕
			if(rank1 == rank2 && (rank1 >= Poker.K)){
				action = ALL_IN;
			}else{
				action = FOLD;
			}
			Logger.Log("not init ok,action="+action);
			thread.sendMsg(action);
			return;
		}
		exchangePocket();
		// 表示已经有多少人Put money in pot,包括自己
		int num_guaranteed = awayFromBtn - foldCnt;
		if(awayFromBtn == 0 || playerCntInInquire == allPlayerNums) {
			// 当inquire里面出现所有玩家的action时，可以直接计算了
			num_guaranteed = playerCntInInquire - foldCnt;
		}
		// still in pot
		int num_inpot = allPlayerNums;
		Logger.Log("playerCntInInquire:"+playerCntInInquire+" awayFromBtn:"+awayFromBtn+" numGua:"+num_guaranteed+" num_inpot:"+num_inpot);
		double probabilityPlay = getProbabilityPlay();
		// 预期参与的玩家,最后加0.5是为了四舍五入
		int expect_num_players = (int) (num_guaranteed + 
					probabilityPlay * (num_inpot - num_guaranteed) + 0.5f);
		Logger.Log("probabilityPlay:"+probabilityPlay+" expect_num_players:"+expect_num_players);
		int group = 0;
		// 找到分组
		if(expect_num_players <= 2) {
			group = PreflopData.TWO_PLAYER;
		}else if(expect_num_players < 5) {
			group = PreflopData.THREEORFOUR_PLAYER;
		}else{
			group = PreflopData.FIVEPLUS_PLAYER;
		}
		// 计算threshhold
		int tightness = 0;
		if(myGameStyle == GameStyle.TIGHT_GAME) {
			tightness = PreflopData.BI_TIGHT;
		}else if(myGameStyle == GameStyle.MODERATE_GAME) {
			tightness = PreflopData.BI_MODERATE;
		}else{
			tightness = PreflopData.BI_LOOSE;
		}
		Logger.Log("group:"+group+" tightness:"+tightness);
		int thresholdMake1 = getThreshold(group, PreflopData.Make1, tightness);
		int thresholdMake2 = getThreshold(group, PreflopData.Make2, tightness);
		int thresholdMake4 = getThreshold(group, PreflopData.Make4, tightness);
		// 接下来查表获得IncomeRate
		int ir = 0;
		if(group == PreflopData.TWO_PLAYER) {
			if(suit1 == suit2) {
				ir = PreflopData.IR2[rank1-2][rank2-2];
			}else{
				ir = PreflopData.IR2[rank2-2][rank1-2];
			}
		}else if(group == PreflopData.THREEORFOUR_PLAYER) {
			if(suit1 == suit2) {
				ir = PreflopData.IR4[rank1-2][rank2-2];
			}else{
				ir = PreflopData.IR4[rank2-2][rank1-2];
			}
		}else{
			if(suit1 == suit2) {
				ir = PreflopData.IR7[rank1-2][rank2-2];
			}else{
				ir = PreflopData.IR7[rank2-2][rank1-2];
			}
		}
		Logger.Log("ir:"+ir+" thresholdMake1:"+thresholdMake1+
				" thresholdMake2:"+thresholdMake2+" thresholdMake4:"+thresholdMake4);
		if(ir >= thresholdMake4) {
			// make4 
			if(rank1 == rank2 && rank1 == Poker.A){
				action = RAISE + (int)(Math.random()*100 + 9*bigBlindBet);
			}else
			if(raisePreFlopCnt >= 2) {
				// raise until raisecnt >= 2, otherwise call.
				action = CALL;
			}else {
				// 要跟的注小于3倍大盲注
				if(minBet < 5 * bigBlindBet) {
					int mount = (int)(Math.random()*100 + 4*bigBlindBet);
					action = RAISE + mount;
				}else{
					action = CALL;
				}
			}
			
		}else if(ir >= thresholdMake2) {
			//  raise if less than two raises have been made this round, 
			// otherwise call.
			// 要下注的倍数
			int betTimes = (minBet / bigBlindBet);
			if(allinCnt > 0) {
				action = FOLD;
			}else if(raisePreFlopCnt >= 2
					|| raiseCnt >= 2) {
				action = CALL;
			}else{
				int mount = (int)(Math.random()*100 + bigBlindBet);
				action = RAISE + mount;
			}
			
			
		}else if(ir >= thresholdMake1) {
			/* fold if it costs two or more bets to continue (and we have not already voluntarily put money in the pot this round), 
			 * otherwise check/call.
			 */
			if(raiseCnt > 1 || raisePreFlopCnt > 1) {
				action = FOLD;
			}else {
				action = CALL;
				// 试试bluff
				int roundNum = ALL_ROUND_NUM - remainTurn;
				if(roundNum >= 20 && raisePreFlopCnt < 1 && minBet <= 40){
					// 局数太少，不bluff
					// 牌差，试试bluff
					if(myCurPos == Position.LATE_POS ||  myCurPos == Position.SBLIND_POS) {
						// 我的上家全部fold
						boolean allFold = true;
						// 弃牌的		 我的上家的数量
						int fold = 0, isNotBehindMe = 0;
						for(Opponent opp : opponents.values()) {
							if(!opp.isBehindMe && opp.curAction.equals(FOLD)){
								fold++;
							}
							isNotBehindMe++;
						}
						float foldRate = fold / (float)isNotBehindMe;
						Logger.Log("我的上家是否all fold?"+allFold+" foldRate="+foldRate);
						if(foldRate > 0.6){
							// 前面的人60%都fold了
							// 看弃牌率
							float foldF = 1f;
							for(String id : aliveOpp) {
								Opponent opp = opponents.get(id);
								float f = (float)opp.getFrequency(State.PRE_FLOP, 1, "fold", 0);
								if(f < foldF) {
									foldF = f;
								}
							}
							Logger.Log("preflop下家弃牌率："+foldF);
							// 要的是最低的弃牌率
							if(foldF > 0.6) {
								// 最小弃牌率都高于60%,bluff
								int mount = (int) (Math.random()*60);
								action = RAISE+mount;
							}
						}
					}
				}
			}
			
		}else{
			// fold if it costs more than zero to continue playing, otherwise check.
			if(minBet > 0) {
				action = FOLD;
			}else {
				action = CALL;
				int roundNum = ALL_ROUND_NUM - remainTurn;
				if(roundNum >= 20){
					// 局数太少，不bluff
					// 牌差，试试bluff
					if(myCurPos == Position.LATE_POS ||  myCurPos == Position.SBLIND_POS) {
						// 我的上家全部fold
						boolean allFold = true;
						for(Opponent opp : opponents.values()) {
							if(!opp.isBehindMe && !opp.curAction.equals(FOLD) && !opp.curAction.equals("blind")){
								allFold = false;
								break;
							}
						}
						if(allFold){
							// 前面的人都fold了
							// 看弃牌率
							float foldF = 1f;
							for(String id : aliveOpp) {
								Opponent opp = opponents.get(id);
								float f = (float)opp.getFrequency(State.PRE_FLOP, 1, "fold", 0);
								if(f < foldF) {
									foldF = f;
								}
							}
							Logger.Log("preflop下家弃牌率："+foldF);
							// 要的是最低的弃牌率
							if(foldF > 0.6) {
								// 最小弃牌率都高于60%,bluff
								int mount = (int) (Math.random()*60);
								action = RAISE+mount;
							}
						}
					}
				}
			}
		}
		// TODO:test
		if(minBet > 40 && !action.equals(FOLD)) {
			// 说明有人加注
			Logger.Log("got raise in preflop");
			int oppir = 0, v = 330;
			for(String id : aliveOpp){
				Opponent opp = opponents.get(id);
				if(opp.curAction.equals("raise") || opp.curAction.equals("all_in")){
					// 我的下家的话，是lastAction，我的上家，是curAction
					int raiseAmount = opp.curBet - opp.preBet;
					Logger.Log(opp.ID+" opp.round_Context:"+opp.round_Context+" opp.betToCall_Context:"+opp.betToCall_Context+" raiseAmount="+raiseAmount);
					float f = (float)opp.getFrequency(opp.round_Context, opp.betToCall_Context, opp.curAction, raiseAmount);
					Logger.Log("f="+f);
					float u = 1 - f;
					int idx = (int) (PreflopData.SortIR7.length * u);
					int tmir = PreflopData.SortIR7[idx];
					if(tmir > oppir){
						oppir = tmir;
						Logger.Log("tmir="+tmir);
					}
				}
			}
			int maxir = oppir + v, minir = ((oppir-v)< (-495)?(-495):(oppir-v));
			if(ir < minir) {
				// 比对方的还小
				action = FOLD;
			}else if(ir >= minir && ir < maxir) {
				if(minBet >= 300 && ir < oppir){
					action = FOLD;
				}else{
					action = CALL;
				}
			}else{
				if(!action.contains(RAISE)){
					// 如果之前没有RAISE
					if(minBet <= 80) {
						action = RAISE;
					}else{
						action = CALL;
					}
				}
			}
		}
				
//		if(allinCnt > 0 && (minBet >= 800 || minBet >= (myAllJetton / 3))) {
//			// 有人all_in
//			if(rank1 == rank2 && rank1 == Poker.A) {
//				action = CALL;
//			}else{
//				action = FOLD;
//			}
//		}
		int roundTurn = ALL_ROUND_NUM - remainTurn;
		if(roundTurn < 10) {
			// 局数太少，不轻易玩
			if(ir < thresholdMake2) {
				action = FOLD;
			}
		}
		if(willWin()) {
			action = FOLD;
		}
		// 确保不会丢牌
		if(action.equals(FOLD)) {
			if(minBet == 0) {
				action = CALL;
			}
		}
		if(action.contains(RAISE)) {
			raisePreFlopCnt++;
		}
		Logger.Log("preflop action="+action);
		thread.sendMsg(action);
		
		
	}
	
	/********** flop策略2 begin***********************/
	
	/**
	 * 
	 * @param bets_to_make the level of strength we want to make
	 * @return
	 */
	private String make(int bets_to_make, int betAmound) {
		int bets_to_call = minBet / bigBlindBet;
		String action = FOLD;
		if(raiseCnt < bets_to_make)
			action = RAISE + betAmound;
		else if(minBet == 0 || bets_to_make >= 2
				|| raiseCnt <= bets_to_make) {
			action = CALL;
		}else{
			action = FOLD;
		}
		
		return action;
	}
	
	/**
	 * 控制加注，防止傻傻的加大注
	 * @param amount
	 * @return =0时表示要改为跟住, -1表示要弃牌
	 */
	private int controlRaiseAmoun(int amount, int handRank){
		int finalRaise = 0;
		boolean isBoardStraight = isBoardStraightDraw();
		int boardSuitedConn = handEval.getBoardSuitedNum();
		boolean isBoardFlush = false;
		if(currentState == State.FLOP) {
			isBoardFlush = (boardSuitedConn == 3);
		}else{
			isBoardFlush = (boardSuitedConn > 3);
		}
		Logger.Log("对方是否可能顺子:"+isBoardStraight+" 是否可能同花:"+isBoardFlush);
		if(isBoardStraight){
			// 对方可能顺子听筒
			if(handRank > HandType.Straight){
				return amount;
			}else if(handRank == HandType.Straight){
				// 我也是顺子,看大小
				boolean bigger = true;
				for(int i = 0; i < shareIdx; i++){
					if(rank1 < shareRank[i]){
						// 我没拿高牌，弃了
						bigger = false;
						break;
					}
				}
				if(bigger){
					return amount;
				}else{
					return 0;
				}
			}else{
				return -1;
			}
		}else
		if(isBoardFlush){
			// 对手可能同花,也就是四张公牌颜色一样
			if(handRank > HandType.Flush){
				return amount;
			}else if(handRank == HandType.Flush){
				// 我也是同花,看大小
				boolean bigger = true;
				for(int i = 0; i < shareIdx; i++){
					if(rank1 < shareRank[i]){
						// 我没拿高牌，弃了
						bigger = false;
						break;
					}
				}
				if(bigger){
					return amount;
				}else{
					return 0;
				}
			}else{
				return -1;
			}
		}else{
			// 看公牌牌型
			int boardType = handEval.getBoardCardType();
			if(handRank == boardType) {
				// 我的最大牌型和公牌一样
				// 包括三条都是公牌，两对都是公牌，一对都是公牌
				// 看我有没有大牌
				boolean bigger = true;
				for(int i = 0; i < shareIdx; i++){
					if(rank1 < shareRank[i]){
						// 我没拿大牌，弃了
						bigger = false;
						break;
					}
				}
				if(bigger){
					return amount;
				}else{
					return 0;
				}
			}else
			if(handRank == HandType.Trips) {
				// 三条，但我手上的不是两条
				if(rank1 != rank2){
					// 说明牌桌上有一对
					int sharedCard = rank1, leftCard = rank2;
					for(int i = 0; i < shareIdx; i++){
						// 找到我手牌上的那一张
						if(shareRank[i] == rank1){
							sharedCard = rank1;
							leftCard = rank2;
							break;
						}
						if(shareRank[i] == rank2){
							sharedCard = rank2;
							leftCard = rank1;
							break;
						}
					}
					boolean bigger = true;
					// 我的剩余那张牌是公牌里最大的
					for(int i = 0; i < shareIdx; i++){
						// 找到我手牌上的那一张
						if(shareRank[i] != sharedCard && shareRank[i] > leftCard){
							bigger = false;
							break;
						}
					}
					if(!bigger){
						return 0;
					}else{
						return amount;
					}
					
				}
			}else
			if(handRank == HandType.TwoPair){
				// 两对,如果我的牌不是最大的
				// 表示两张最大的公牌
				int board1 = -1, board2 = -1;
				for(int i = 0; i < shareIdx; i++) {
					if(shareRank[i] > board1){
						board1 = shareRank[i];
					}
				}
				for(int i = 0; i < shareIdx; i++) {
					if(shareRank[i] == board1){
						continue;
					}
					if(shareRank[i] > board2 ){
						board2 = shareRank[i];
					}
				}
				// 如果我的最大牌比board1小，弃牌
				if(rank1 < board1){
					return 0;
				}else if(rank1 == board1){
					// 牌二不是次2大
					if(rank2 < board2){
						return 0;
					}
				}
			}else
			if(handRank == HandType.Pair){
				// 只有一对，而且第一大底牌不是最大的
				boolean bigger = true;
				for(int i = 0; i < shareIdx; i++) {
					if(shareRank[i] > rank1) {
						bigger = false;
						break;
					}
				}
				if(bigger){
					// 看第二张底牌,如果不是最大的，也不要加注
					for(int i = 0; i < shareIdx; i++){
						if(shareRank[i] == rank1){
							continue;
						}
						if(shareRank[i] > rank2) {
							bigger = false;
							break;
						}
					}
				}
				if(!bigger) {
					return 0;
				}else{
					return amount;
				}
			}
		}
		return amount;
	}
	
	private void makeTurnAction() {
		int handRank = getHandRank();
		if(rank1 == rank2 && rank1 == Poker.A) {
			// 对K，如果牌面上都是高牌而且不同色，all_in
			int boardType = handEval.getBoardCardType();
			int suitNum = handEval.getBoardSuitedNum();
			boolean bigger = true;	
			if(boardType < 1 && suitNum < 3) {
				// 没有大于K的高牌
				thread.sendMsg(ALL_IN);
				return;
			}
		}
//		if(rank1 == rank2 && rank1 == Poker.K){
//			// 对K，如果牌面上都是高牌而且不同色，all_in
//			int boardType = handEval.getBoardCardType();
//			int suitNum = handEval.getBoardSuitedNum();
//			boolean bigger = true;	
//			for(int i = 0; i < shareIdx; i++){
//				if(shareRank[i] == Poker.A){
//					bigger = false;
//					break;
//				}
//			}
//			if(bigger && boardType < 1 && suitNum < 3) {
//				// 没有大于K的高牌
//				thread.sendMsg(ALL_IN);
//				return;
//			}
//		}
		String action = FOLD;
		switch(handRank){
		case HandType.StraightFlush:
		case HandType.FourOfAKind:
			if(currentState == State.FLOP){
				action = ALL_IN;
				thread.sendMsg(action);
				return;
			}
			break;
		}
		if(currentState == State.FLOP || minBet > 0) {
			semiBluffFlag = false;
		}
		int raisefactor = 2;
		if(myGameStyle == GameStyle.LOOSE_GAME) {
			raisefactor = 3;
		}else if(myGameStyle == GameStyle.TIGHT_GAME){
			raisefactor = 1;
		}
		int raiseAmount = handRank * bigBlindBet*raisefactor;
		double ppot = 0;
		double ehs = 0;
		double strength = 0;
		long startTime = System.currentTimeMillis(); //获取结束时间  
		double calEhsRes[] = calEHS();
		long endTime = System.currentTimeMillis(); //获取结束时间  
		Logger.Log("calEHS()运行时间： "+(endTime-startTime)+"ms");
//		if(!done) {
//			// not done
//			Logger.Log("calEHS not done!");
//			action = FOLD;
//			switch(handRank){
//			case HandType.FullHouse:
//				if(currentState == State.FLOP){
//					action = ALL_IN;
//				}else{
//					action = RAISE;
//				}
//				break;
//			case HandType.Flush:
//			case HandType.Straight:
//			case HandType.Trips:
//				action = CALL;
//				break;
//			}
//			thread.sendMsg(action);
//			return;
//		}
		ppot = calEhsRes[0];
		ehs = calEhsRes[1];		
		Logger.Log("ehs="+ehs+" ppot="+ppot);
		// 可以看做是阈值
		double make1 = 0.5f, make2 = 0.85f;
		if(myGameStyle == GameStyle.LOOSE_GAME) {
			make1 = 0.4f;
			make2 = 0.8f;
		}else if(myGameStyle == GameStyle.TIGHT_GAME) {
			make1 = 0.6f;
			make2 = 0.9f;
		}
		if(ehs >= make2) {
			// 实际加注的是上家加的最小注+raiseAmount
			int totalRaise = minBet + (minBet > raiseAmount? minBet:raiseAmount);
			action = make(2, totalRaise);
			// 控制加注！！
			if(action.contains(RAISE) && totalRaise > 200){
				Logger.Log("控制加注");
				int r = controlRaiseAmoun(totalRaise, handRank);
				if(r == 0){
					action = CALL;
					Logger.Log("修改为call");
				}else if(r == -1){
					Logger.Log("修改为fold!");
					action = FOLD;
				}
			}
			
		}else if(ehs >= make1) {
			// 介于0.5~0.85之间
			if(ehs <= 0.7){
				raiseAmount = (int) (Math.random()*100);
			}
			action = make(1, raiseAmount);
			// TODO:控制加注
			// 看竞争对手有一个raise时的弃牌率
			float foldF = 1;
			for(String id : aliveOpp) {
				Opponent opp = opponents.get(id);
				float f = (float)opp.getFrequency(currentState, 1, "fold", 0);
				if(f < foldF) {
					foldF = f;
				}
			}
			if(foldF < 0.5) {
				// 对手弃牌的概率小于一半
				action = CALL;
			}
			// 再加注就太大了
//			if(minBet >= 240){
//				// 对手弃牌的概率小于一半
//				action = CALL;
//			}
//			// 0.6~0.8:(0,0.5,0.5) 也就是0.5会bluff
//			// 0.5~0.6:(0.1,0.8,0.1) 只有一成概率会bluff
//			int callThreshold = 50;
//			int raiseThreshold = 50;
//			int foldThreshold = 0;
//			if(ehs < 0.6) {
//				callThreshold = 80;
//				raiseThreshold = 10;
//				foldThreshold = 10;
//			}
//			int random = (int) Math.random()*100;
//			if(random <= foldThreshold) {
//				action = FOLD;
//			}else if(random <= callThreshold) {
//				action = CALL;
//			}else{
//				if(minBet > 0) {
//					action = CALL;
//				}else{
//					// 也就是bluff，要考虑弃牌率
//					// 看竞争对手有一个raise时的弃牌率
//					float foldF = 1;
//					for(String id : aliveOpp) {
//						Opponent opp = opponents.get(id);
//						float f = (float)opp.getFrequency(currentState, 1, "fold", 0);
//						Logger.Log(opp.ID+"when semi-bluff fold rate:"+f);
//						if(f < foldF) {
//							foldF = f;
//						}
//					}
//					Logger.Log("minfoldf="+foldF);
//					if(foldF <= 0.5){
//						// 弃牌率不高，就不bluff了
//						action = CHECK;
//					}else{						
//						int mount = (int)(Math.random()*100);
//						action = RAISE+mount;
//					}
//				}
//			}
		}else if(minBet == 0){
			// 没人加注，考虑semi-bluff
			raiseAmount = (int)(Math.random()*80);
			double potOdds2 = 2*raiseAmount / 
					(double)(totalPot + curHandPlayerNums*raiseAmount + 2*raiseAmount);
			Logger.Log("semi-bluff:ppot="+ppot+ " potOdds2:"+potOdds2);
			if((currentState != State.RIVER && ppot >= potOdds2))
			{
				// 看竞争对手有一个raise时的弃牌率
				float foldF = 1;
				for(String id : aliveOpp) {
					Opponent opp = opponents.get(id);
					float f = (float)opp.getFrequency(currentState, 1, "fold", 0);
					Logger.Log(opp.ID+"when semi-bluff fold rate:"+f);
					if(f < foldF) {
						foldF = f;
					}
				}
				Logger.Log("minfoldf="+foldF);
				if(foldF <= 0.6){
					// 弃牌率不高，就不bluff了
					action = CHECK;
				}else{
					semiBluffFlag = true;
					action = RAISE+raiseAmount;
				}
			}else{
				action = CHECK;
			}
			
		}else{
			// check pot odds
			double potOdds = minBet / (double)(totalPot + minBet);
			if(currentState == State.TURN && ppot >= potOdds) {
				action = CALL;
			}else if(currentState == State.RIVER && ehs >= potOdds) {
				action = CALL;
			}else{
				action = FOLD;
			}
		}
		// TODO:test
		if(minBet > 100 && !action.equals(FOLD)) {
			// 风险判断
			// 说明有人加注
			Logger.Log("got raise");
			float oppu = 0, oppv = 0;
			for(String id : aliveOpp){
				Opponent opp = opponents.get(id);
				if(opp.curAction.equals("raise") || opp.curAction.equals(ALL_IN)){
					int oppRaiseNum = opp.curBet - opp.preBet;
					Logger.Log(opp.ID+" opp.round_Context:"+opp.round_Context+" opp.betToCall_Context:"+opp.betToCall_Context+" oppRaiseNum="+oppRaiseNum);
					float f = (float)opp.getFrequency(opp.round_Context, opp.betToCall_Context, opp.curAction, oppRaiseNum);
					float u = 1 - f;
					float v = 0.4f*(1-u);
					if(u > oppu) {
						// 看哪家的平均牌力最高
						oppu = u;
						oppv = v;
					}
					Logger.Log("f="+f+"u="+u+" v="+v);
				}
			}
			float maxu = ((oppu+oppv) <= 1 ? (oppu+oppv):1), minu = ((oppu-oppv) > 0 ? (oppu-oppv) : (oppu));
			Logger.Log("maxu="+maxu+" minu="+minu);
			if(ehs < minu) {
				// 比下限还小，就不跟了吧
				if(minBet > 40) {
					action = FOLD;
				}else{
					action = CALL;
				}
			}else if(ehs < maxu && ehs > minu) {
				if((minBet >= 400 && ehs < oppu)) {
					action = FOLD;
				}else{
					// 照跟
					action = CALL;
				}
			}else{
				if(!action.contains(RAISE)){
//					// 之前的决策不是raise，那照跟
					action = CALL;
//					if(currentState == State.FLOP){
//						// 唬我？虎回去！
////						action = RAISE;
//					}else{
//						action = CALL;
//					}
				}
			}
		}
		if(minBet >= 300 && !action.equals(FOLD) && currentState != State.FLOP){
			// 到了turn, river阶段就不能靠统计数据来止损了，毕竟数据较少
			// 首先看最常见的是不是同花，顺子听筒
			boolean isBoardStraight = isBoardStraightDraw();
			int boardSuitedConn = handEval.getBoardSuitedNum();
			boolean isBoardFlush = false;
			if(currentState == State.FLOP) {
				isBoardFlush = (boardSuitedConn == 3);
			}else{
				isBoardFlush = (boardSuitedConn > 3);
			}
			Logger.Log("对方是否可能顺子:"+isBoardStraight+" 是否可能同花:"+isBoardFlush);
			if(isBoardStraight){
				// 对方可能顺子听筒
				if(handRank > HandType.Straight){
					action = RAISE;
				}else if(handRank == HandType.Straight){
					// 我也是顺子,看大小
					boolean bigger = true;
					for(int i = 0; i < shareIdx; i++){
						if(rank1 < shareRank[i]){
							// 我没拿高牌，弃了
							bigger = false;
							break;
						}
					}
					if(bigger){
						action = CALL;
					}else{
						action = FOLD;
					}
				}else{
					action = FOLD;
				}
			}else
			if(isBoardFlush){
				// 对手可能同花,也就是四张公牌颜色一样
				if(handRank > HandType.Flush){
					action = RAISE;
				}else if(handRank == HandType.Flush){
					// 我也是同花,看大小
					boolean bigger = true;
					for(int i = 0; i < shareIdx; i++){
						if(rank1 < shareRank[i]){
							// 我没拿高牌，弃了
							bigger = false;
							break;
						}
					}
					if(bigger){
						action = CALL;
					}else{
						action = FOLD;
					}
				}else{
					action = FOLD;
				}
			}else{
				// 看公牌牌型
				int boardType = handEval.getBoardCardType();
				if(handRank == boardType) {
					// 我的最大牌型和公牌一样
					// 包括三条都是公牌，两对都是公牌，一对都是公牌
					// 看我有没有大牌
					boolean bigger = true;
					for(int i = 0; i < shareIdx; i++){
						if(rank1 < shareRank[i]){
							// 我没拿大牌，弃了
							bigger = false;
							break;
						}
					}
					if(bigger){
						action = CALL;
					}else{
						action = FOLD;
					}
				}else
				if(handRank == HandType.Trips) {
					// 三条，但我手上的不是两条
					if(rank1 != rank2){
						// 说明牌桌上有一对
						int sharedCard = rank1, leftCard = rank2;
						for(int i = 0; i < shareIdx; i++){
							// 找到我手牌上的那一张
							if(shareRank[i] == rank1){
								sharedCard = rank1;
								leftCard = rank2;
								break;
							}
							if(shareRank[i] == rank2){
								sharedCard = rank2;
								leftCard = rank1;
								break;
							}
						}
						boolean bigger = true;
						// 我的剩余那张牌是公牌里最大的
						for(int i = 0; i < shareIdx; i++){
							// 找到我手牌上的那一张
							if(shareRank[i] != sharedCard && shareRank[i] > leftCard){
								bigger = false;
								break;
							}
						}
						if(!bigger){
							action = FOLD;
						}else{
							action = CALL;
						}
						
					}
				}else
				if(handRank == HandType.TwoPair){
					// 两对,如果我的牌不是最大的
					// 表示两张最大的公牌
					int board1 = -1, board2 = -1;
					for(int i = 0; i < shareIdx; i++) {
						if(shareRank[i] > board1){
							board1 = shareRank[i];
						}
					}
					for(int i = 0; i < shareIdx; i++) {
						if(shareRank[i] == board1){
							continue;
						}
						if(shareRank[i] > board2 ){
							board2 = shareRank[i];
						}
					}
					// 如果我的最大牌比board1小，弃牌
					if(rank1 < board1){
						action = FOLD;
					}else if(rank1 == board1){
						// 牌二不是次2大
						if(rank2 < board2){
							action = FOLD;
						}
					}
				}else
				if(handRank == HandType.Pair){
					// 只有一对，而且不是最大的
					boolean bigger = true;
					for(int i = 0; i < shareIdx; i++) {
						if(shareRank[i] > rank1) {
							bigger = false;
							break;
						}
					}
					if(!bigger) {
						action = FOLD;
					}else{
						action = CALL;
					}
				}
			}
		}
//		if(action.equals(ALL_IN) || (action.equals(CALL) || action.contains(RAISE))) {
//			int factor = 3;
//			if(ehs >= make2){
//				factor = 12;
//			}else if(ehs >= make1) {
//				factor = 6;
//			}
//			if(dangerRaise || (currentState == State.FLOP && raiseFLopCnt >= 2)
//			|| (currentState == State.TURN && raiseTurnCnt >= 2)
//			|| (currentState == State.RIVER && raiseRiverCnt >= 2)
//			|| minBet >= factor * bigBlindBet) {
//				// 自己加了两次，说明自己加注之后对方还跟住！
//				// 风险控制
//				boolean danger = false;
//				Logger.Log("in makeTurnAction2: aliveOppSize="+aliveOpp.size());
//				for(String id : aliveOpp) {
//					if(danger) break;
//					Opponent aliveOpponet = opponents.get(id);
//					Logger.Log("in makeTurnAction2: opp curaction="+aliveOpponet.curAction);
//					if(aliveOpponet != null && 
//					   (aliveOpponet.curAction.contains(RAISE)
//					   || aliveOpponet.curAction.equals(ALL_IN)))
//					{
//						int round = ALL_ROUND_NUM - remainTurn;
//						double foldPreFlopOdds = (double)aliveOpponet.foldBeforeFlop / round;
//						double af = (double)(aliveOpponet.raiseCnt + aliveOpponet.allinCnt*2)
//								/ (aliveOpponet.callCnt + aliveOpponet.checkCnt);
//						Logger.Log("in makeTurnAction2: foldPreFlopOdds="+foldPreFlopOdds
//								+" af:" + af);
//						if(foldPreFlopOdds >= 0.65f || af <= 0.28) {
//							// 很少加注或者经常preflop弃牌
//							danger = true;
//						}else{							
//							if(dangerRaise){
//								if(ehs < make2) {
//									danger = true;
//								}
//							}
//						}
//					}
//				}
//				if(danger){
//					action = FOLD;
//				}
//			}
//		}
//		// 一次加注1000
//		if(minBet >= ((myAllJetton+myAllMoney)/2) || minBet >= 1000) {
//			// 一下子扔掉一半的钱
//			action = FOLD;
//			switch(handRank){
//			case HandType.StraightFlush:
//			case HandType.FourOfAKind:
//				action = ALL_IN;
//				break;
//			case HandType.FullHouse:
//				action = CALL;
//				break;
//			}
//		}
		if(((minBet >= (myAllJetton/2) && myAllJetton > 1000) || minBet >= 900)
				&& !action.equals(FOLD)){
			// 一次扔掉一半
			if(handRank < HandType.Flush){
				action = FOLD;
			}
		}
		if(action.contains(RAISE)) {
			if((currentState == State.FLOP && raiseFLopCnt >= 2 )||
			   (currentState == State.TURN && raiseTurnCnt >= 2) ||
			   (currentState == State.RIVER && raiseRiverCnt >= 2))
			{
				action = CALL;
			}else{
				if(currentState == State.FLOP){
					raiseFLopCnt++;
				}
				if(currentState == State.TURN){
					raiseTurnCnt++;
				}
				if(currentState == State.RIVER){
					raiseRiverCnt++;
				}
			}
		}
		if(minBet == 0 && action.equals(FOLD)) {
			action = CALL;
		}
		if(willWin()) {
			action = FOLD;
		}
		Logger.Log("in turnaction="+action);
		thread.sendMsg(action);
	}
	
	
	private void doAction() {
		if(currentState == State.PRE_FLOP) {
			try{
				makePreFlopAction();
			}catch(Exception ex){
				String msg = "";
				StackTraceElement ele[] = ex.getStackTrace();
				for(StackTraceElement e : ele){
					msg += e.getMethodName()+" line:"+e.getLineNumber()+"\n";
				}
				Logger.Log("err:"+ex.getMessage()+"\n"+msg);
				thread.sendMsg(FOLD);
			}
		}else{
			makeTurnAction();
		}
	}
	
	/**
	 * 记录各玩家的frequency，并且更新权重表
	 */
	private void recordAndReweight() {
		// 先将上一条消息的上家和下家拼起来，这样在处理当前消息的下家时才是完整的
		LinkedList<InqNotifyMsg> mergeMsgList = new LinkedList<InqNotifyMsg>();
		mergeMsgList.addAll(preMsgList);
		mergeMsgList.addAll(curMsgList);
		int countRaise = 0;
		for(InqNotifyMsg inqMsg : curMsgList) {
			if(inqMsg.isBehindMe) {
				if(inqMsg.action.contains("blind")){
					continue;
				}
				if(!inqMsg.action.equals("check")
						&& inqMsg.action.equals(inqMsg.preAction)
						&& inqMsg.preBet == inqMsg.curBet)
				{
					// 如果行为没变，而且不是check(如果是check，则在不同round的
					// 时候行为也可能相同)
					continue;
				}
				if(inqMsg.ID.equals(ID)){
					// 我就不用继续执行下面的动作了
					continue;
				}
				int tmpRaiseCnt = 0;
				// 从后往前遍历，从当前的下家的ID开始计数，遇到一个raise+1
				// 直到再次遇到自己的ID或者遍历完毕
				ListIterator<InqNotifyMsg> listIt = mergeMsgList.listIterator(mergeMsgList.size());
				boolean beginFlag = false;
				while(listIt.hasPrevious()) {
					InqNotifyMsg tmpMsg = listIt.previous();
					if(tmpMsg.ID.equals(inqMsg.ID)) {
						if(beginFlag) {
							// 已经遇到过一次ID，说明已经开始计算，再次遇到就应该break
							break;
						}else{
							beginFlag = true;
							continue;
						}
					}
					if((tmpMsg.action.equals("raise") || tmpMsg.action.equals(ALL_IN))
							&& tmpMsg.preBet != tmpMsg.curBet) {
						// 算是一个加注
						tmpRaiseCnt++;
					}
				}
				final Opponent opp = opponents.get(inqMsg.ID);
				if(opp != null) {
					// TODO 开始统计和reweight
					State rightState = currentState;
					if(circle == 1) {
						// 新一轮的第一圈，说明是上一轮的行为
						if(currentState == State.FLOP) {
							rightState = State.PRE_FLOP;
						}else if(currentState == State.TURN) {
							rightState = State.FLOP;
						}else if(currentState == State.RIVER) {
							rightState = State.TURN;
						}
					}
					// TODO 先统计后reweight
					int raiseAmount = opp.curBet - opp.preBet;
					opp.round_Context = rightState;
					opp.betToCall_Context = tmpRaiseCnt;
					opp.handleAction(rightState, tmpRaiseCnt, inqMsg.action, raiseAmount);						
//					if(circle == 1) {
//						// 新一轮的第一圈，说明是上一轮的行为
//						if(currentState == State.FLOP) {
//							rightState = State.PRE_FLOP;
//						}else if(currentState == State.TURN) {
//							rightState = State.FLOP;
//						}else if(currentState == State.RIVER) {
//							rightState = State.TURN;
//						}
//					}
//					float u = 0, v = 0;
//					if(inqMsg.action.equals("call") || inqMsg.action.equals("check")){
//						// call的话，要先计算加注的牌力和弃牌的牌力，平均才是跟住的牌力
//						float raiseu = 1-((float) opp.getFrequency(rightState, tmpRaiseCnt, inqMsg.action));
//						float foldu = (float) opp.getFrequency(rightState, tmpRaiseCnt, inqMsg.action);
//						u = (raiseu + foldu) / 2;
//					}else if(inqMsg.action.equals("raise")){
//						u = 1 - (float)opp.getFrequency(rightState, tmpRaiseCnt, inqMsg.action);
//					}else{
//						u = (float)opp.getFrequency(rightState, tmpRaiseCnt, inqMsg.action);
//					}
//					v = 0.4f*(1-u);
//					System.out.println("u="+u+" v="+v);
//					if(rightState != State.PRE_FLOP) {
//						handEval.postflopReweight(u, v, opp.weight, rightState);
//					}
				}else{
					Logger.Log("in recordAndReweight(), opp=null!");
				}
			}else{
				// 是我的上家，直接记录
				if(inqMsg.action.contains("blind")){
					continue;
				}
				if(!inqMsg.action.equals("check")
					&& inqMsg.action.equals(inqMsg.preAction)
					&& inqMsg.preBet == inqMsg.curBet)
				{
					// 如果行为没变，而且不是check(如果是check，则在不同round的
					// 时候行为也可能相同)
					continue;
				}
				if(circle > 1) {
					// 可能是river阶段补发的notify
					if(inqMsg.action.equals("check")
							&& inqMsg.action.equals(inqMsg.preAction)
							&& inqMsg.preBet == inqMsg.curBet)
						{
							// 如果行为没变，而且不是check(如果是check，则在不同round的
							// 时候行为也可能相同)
							continue;
						}
					// 第二圈，说明有可能是当前的ID的选手的下家加注了，要遍历上一条msg
					int tmpRaiseCnt = 0;
					// 从后往前遍历，从当前的上家的ID开始计数，遇到一个raise+1
					// 直到再次遇到自己的ID或者遍历完毕
					ListIterator<InqNotifyMsg> listIt = mergeMsgList.listIterator(mergeMsgList.size());
					boolean beginFlag = false;
					while(listIt.hasPrevious()) {
						InqNotifyMsg tmpMsg = listIt.previous();
						if(tmpMsg.ID.equals(inqMsg.ID)) {
							if(beginFlag) {
								// 已经遇到过一次ID，说明已经开始计算，再次遇到就应该break
								break;
							}else{
								beginFlag = true;
								continue;
							}
						}
						if((tmpMsg.action.equals("raise") || tmpMsg.action.equals(ALL_IN))
								&& tmpMsg.preBet != tmpMsg.curBet) {
							// 算是一个加注
							tmpRaiseCnt++;
						}
					}
					Opponent opp = opponents.get(inqMsg.ID);
					if(opp != null) {
						// TODO 开始统计和reweight
						int raiseAmount = opp.curBet - opp.preBet;
						opp.round_Context = currentState;
						opp.betToCall_Context = tmpRaiseCnt;
						opp.handleAction(currentState, tmpRaiseCnt, inqMsg.action, raiseAmount);
//						float u = 1, v = 0.5f;
//						if(inqMsg.action.equals("call") || inqMsg.action.equals("check")){
//							// call的话，要先计算加注的牌力和弃牌的牌力，平均才是跟住的牌力
//							float raiseu = 1-((float) opp.getFrequency(currentState, tmpRaiseCnt, "raise"));
//							float foldu = (float) opp.getFrequency(currentState, tmpRaiseCnt, "fold");
//							u = (raiseu + foldu) / 2;
//						}else if(inqMsg.action.equals("raise")){
//							u = 1 - (float)opp.getFrequency(currentState, tmpRaiseCnt, inqMsg.action);
//						}else{
//							u = (float)opp.getFrequency(currentState, tmpRaiseCnt, inqMsg.action);
//						}
//						v = 0.4f*(1-u);
//						System.out.println("u="+u+" v="+v);
//						if(currentState != State.PRE_FLOP) {
//							handEval.postflopReweight(u, v, opp.weight, currentState);
//						}
					}else{
						Logger.Log("in recordAndReweight(), opp=null!");
					}
				}else{
					// circle = 1，只要看该玩家的上家就好了
					Opponent opp = opponents.get(inqMsg.ID);
					if(opp != null) {
						// TODO 开始统计和reweight
						int raiseAmount = opp.curBet - opp.preBet;
						opp.round_Context = currentState;
						opp.betToCall_Context = countRaise;
						opp.handleAction(currentState, countRaise, inqMsg.action, raiseAmount);
//						float u = 0, v = 0;
//						if(inqMsg.action.equals("call")){
//							// call的话，要先计算加注的牌力和弃牌的牌力，平均才是跟住的牌力
//							float raiseu = 1-((float) opp.getFrequency(currentState, countRaise, inqMsg.action));
//							float foldu = (float) opp.getFrequency(currentState, countRaise, inqMsg.action);
//							u = (raiseu + foldu) / 2;
//						}else if(inqMsg.action.equals("raise")){
//							u = 1 - (float)opp.getFrequency(currentState, countRaise, inqMsg.action);
//						}else{
//							u = (float)opp.getFrequency(currentState, countRaise, inqMsg.action);
//						}
//						v = 0.4f*(1-u);
//						if(currentState != State.PRE_FLOP) {
//							handEval.postflopReweight(u, v, opp.weight, currentState);
//						}
					}else{
						Logger.Log("in recordAndReweight(), opp=null!");
					}
					if((inqMsg.action.equals("raise")|| inqMsg.action.equals(ALL_IN)) 
							&& inqMsg.preBet != inqMsg.curBet) {
						// 算是一个加注
						countRaise++;
					}
				}
				
			}
		}
	}
	
	/**
	 * 将主方法分解，便于开线程计算
	 * @param mergeMsgList
	 * @param inqMsg
	 * @param needReweight 是否需要reweight
	 */
	private void subRecordReweight(final LinkedList<InqNotifyMsg> mergeMsgList, 
			final InqNotifyMsg inqMsg, final boolean needReweight, final int countRaise)
	{
		if(inqMsg.isBehindMe) {
			int tmpRaiseCnt = 0;
			// 从后往前遍历，从当前的下家的ID开始计数，遇到一个raise+1
			// 直到再次遇到自己的ID或者遍历完毕
			ListIterator<InqNotifyMsg> listIt = mergeMsgList.listIterator(mergeMsgList.size());
			boolean beginFlag = false;
			while(listIt.hasPrevious()) {
				InqNotifyMsg tmpMsg = listIt.previous();
				if(tmpMsg.ID.equals(inqMsg.ID)) {
					if(beginFlag) {
						// 已经遇到过一次ID，说明已经开始计算，再次遇到就应该break
						break;
					}else{
						beginFlag = true;
						continue;
					}
				}
				if(tmpMsg.action.equals("raise") && tmpMsg.preBet != tmpMsg.curBet) {
					// 算是一个加注
					tmpRaiseCnt++;
				}
			}
			final Opponent opp = opponents.get(inqMsg.ID);
			if(opp != null) {
				// TODO 开始统计和reweight
				State rightState = currentState;
				if(circle == 1) {
					// 新一轮的第一圈，说明是上一轮的行为
					if(currentState == State.FLOP) {
						rightState = State.PRE_FLOP;
					}else if(currentState == State.TURN) {
						rightState = State.FLOP;
					}else if(currentState == State.RIVER) {
						rightState = State.TURN;
					}
				}
				// 记录上下文
				opp.round_Context = rightState;
				opp.betToCall_Context = tmpRaiseCnt;
				// TODO 先统计后reweight
				int raiseAmount = opp.curBet - opp.preBet;
				opp.handleAction(rightState, tmpRaiseCnt, inqMsg.action, raiseAmount);
				if(!needReweight) {
					return;
				}
				float u = 0, v = 0;
				if(inqMsg.action.equals("call") || inqMsg.action.equals("check")){
					// call的话，要先计算加注的牌力和弃牌的牌力，平均才是跟住的牌力
					float raiseu = 1-((float) opp.getFrequency(rightState, tmpRaiseCnt, inqMsg.action, -1));
					float foldu = (float) opp.getFrequency(rightState, tmpRaiseCnt, inqMsg.action, 0);
					u = (raiseu + foldu) / 2;
				}else if(inqMsg.action.equals("raise") || inqMsg.action.equals(ALL_IN)){
					u = 1 - (float)opp.getFrequency(rightState, tmpRaiseCnt, inqMsg.action, raiseAmount);
				}else{
					u = (float)opp.getFrequency(rightState, tmpRaiseCnt, inqMsg.action, 0);
				}
				v = 0.4f*(1-u);
				Logger.Log("u="+u+" v="+v);
				if(rightState != State.PRE_FLOP) {
					handEval.fastPostflopReweight(u, v, opp.weight, rightState);
				}
			}
		}if(circle > 1) {
			// 可能是river阶段补发的notify
			if(inqMsg.action.equals("check")
					&& inqMsg.action.equals(inqMsg.preAction)
					&& inqMsg.preBet == inqMsg.curBet)
				{
					// 如果行为没变，而且不是check(如果是check，则在不同round的
					// 时候行为也可能相同)
					return;
				}
			// 第二圈，说明有可能是当前的ID的选手的下家加注了，要遍历上一条msg
			int tmpRaiseCnt = 0;
			// 从后往前遍历，从当前的上家的ID开始计数，遇到一个raise+1
			// 直到再次遇到自己的ID或者遍历完毕
			ListIterator<InqNotifyMsg> listIt = mergeMsgList.listIterator(mergeMsgList.size());
			boolean beginFlag = false;
			while(listIt.hasPrevious()) {
				InqNotifyMsg tmpMsg = listIt.previous();
				if(tmpMsg.ID.equals(inqMsg.ID)) {
					if(beginFlag) {
						// 已经遇到过一次ID，说明已经开始计算，再次遇到就应该break
						break;
					}else{
						beginFlag = true;
						continue;
					}
				}
				if(tmpMsg.action.equals("raise") && tmpMsg.preBet != tmpMsg.curBet) {
					// 算是一个加注
					tmpRaiseCnt++;
				}
			}
			Opponent opp = opponents.get(inqMsg.ID);
			if(opp != null) {
				// TODO 开始统计和reweight
				// 记录上下文
				opp.round_Context = currentState;
				opp.betToCall_Context = tmpRaiseCnt;
				int raiseAmount = opp.curBet - opp.preBet;
				opp.handleAction(currentState, tmpRaiseCnt, inqMsg.action, raiseAmount);
				if(!needReweight){
					return;
				}
				float u = 1, v = 0.5f;
				if(inqMsg.action.equals("call") || inqMsg.action.equals("check")){
					// call的话，要先计算加注的牌力和弃牌的牌力，平均才是跟住的牌力
					float raiseu = 1-((float) opp.getFrequency(currentState, tmpRaiseCnt, RAISE, -1));
					float foldu = (float) opp.getFrequency(currentState, tmpRaiseCnt, FOLD, 0);
					u = (raiseu + foldu) / 2;
				}else if(inqMsg.action.equals(RAISE) || inqMsg.action.equals(ALL_IN)){
					u = 1 - (float)opp.getFrequency(currentState, tmpRaiseCnt, inqMsg.action, raiseAmount);
				}else{
					u = (float)opp.getFrequency(currentState, tmpRaiseCnt, inqMsg.action, 0);
				}
				v = 0.4f*(1-u);
				Logger.Log("u="+u+" v="+v);
				if(currentState != State.PRE_FLOP) {
					handEval.fastPostflopReweight(u, v, opp.weight, currentState);
				}
			}
		}else{
			// circle = 1，只要看该玩家的上家就好了
			Opponent opp = opponents.get(inqMsg.ID);
			if(opp != null) {
				// TODO 开始统计和reweight
				opp.round_Context = currentState;
				opp.betToCall_Context = countRaise;
				int raiseAmount = opp.curBet - opp.preBet;
				opp.handleAction(currentState, countRaise, inqMsg.action, raiseAmount);
				if(!needReweight){
					return;
				}
				float u = 0, v = 0;
				if(inqMsg.action.equals("call") || inqMsg.action.equals("check")){
					// call的话，要先计算加注的牌力和弃牌的牌力，平均才是跟住的牌力
					float raiseu = 1-((float) opp.getFrequency(currentState, countRaise, inqMsg.action, -1));
					float foldu = (float) opp.getFrequency(currentState, countRaise, inqMsg.action, 0);
					u = (raiseu + foldu) / 2;
				}else if(inqMsg.action.equals(RAISE) || inqMsg.action.equals(ALL_IN)){
					u = 1 - (float)opp.getFrequency(currentState, countRaise, inqMsg.action, raiseAmount);
				}else{
					u = (float)opp.getFrequency(currentState, countRaise, inqMsg.action, 0);
				}
				v = 0.4f*(1-u);
				if(currentState != State.PRE_FLOP) {
					handEval.fastPostflopReweight(u, v, opp.weight, currentState);
				}
			}
		}
	}
	/**
	 * 更新权重表会超时
	 */
//	private boolean threadFlag = false;
//	private int threadCount = 0;
//	private void recordAndReweight() {
//		final CountDownLatch countDownLatch = new CountDownLatch(curMsgList.size());//初始化计数器
//		// 先将上一条消息的上家和下家拼起来，这样在处理当前消息的下家时才是完整的
//		final LinkedList<InqNotifyMsg> mergeMsgList = new LinkedList<InqNotifyMsg>();
//		mergeMsgList.addAll(preMsgList);
//		mergeMsgList.addAll(curMsgList);
//		int countRaise = 0;
//		for(final InqNotifyMsg inqMsg : curMsgList) {
//			if(inqMsg.isBehindMe) {
//				if(!inqMsg.action.equals("check")
//						&& inqMsg.action.equals(inqMsg.preAction)
//						&& inqMsg.preBet == inqMsg.curBet)
//				{
//					// 如果行为没变，而且不是check(如果是check，则在不同round的
//					// 时候行为也可能相同)
//					continue;
//				}
//				if(inqMsg.ID.equals(ID)){
//					// 我就不用继续执行下面的动作了
//					continue;
//				}
//				boolean needReweight = true;
//				if(myCurAction.equals(FOLD)){
//					needReweight = false;
//				}
//				// 不要reweight了，太耗时了
//				subRecordReweight(mergeMsgList, inqMsg, false, countRaise);
////				threadFlag = needReweight;
////				threadCount = countRaise;
////				threadPool.execute(new Runnable() {
////					
////					@Override
////					public void run() {
////						long startTime=System.currentTimeMillis();   //获取开始时间  
////						subRecordReweight(mergeMsgList, inqMsg, threadFlag, threadCount);
////						countDownLatch.countDown();
////						long endTime=System.currentTimeMillis(); //获取结束时间  
////						Logger.Log("subRecordReweight运行时间： "+(endTime-startTime)+"ms"); 
////					}
////				});
//				
//			}else{
//				// 是我的上家，直接记录
//				if(inqMsg.action.contains("blind")){
//					continue;
//				}
//				if(!inqMsg.action.equals("check")
//					&& inqMsg.action.equals(inqMsg.preAction)
//					&& inqMsg.preBet == inqMsg.curBet)
//				{
//					// 如果行为没变，而且不是check(如果是check，则在不同round的
//					// 时候行为也可能相同)
//					continue;
//				}
//					boolean needReweight = true;
//					if(myCurAction.equals(FOLD)){
//						needReweight = false;
//					}
//					// 不要reweight了，太耗时了
//					subRecordReweight(mergeMsgList, inqMsg, false, countRaise);
////					threadFlag = needReweight;
////					threadCount = countRaise;
////					threadPool.execute(new Runnable() {
////						
////						@Override
////						public void run() {
////							long startTime=System.currentTimeMillis();   //获取开始时间  
////							subRecordReweight(mergeMsgList, inqMsg, threadFlag, threadCount);
////							countDownLatch.countDown();
////							long endTime=System.currentTimeMillis(); //获取结束时间  
////							Logger.Log("subRecordReweight运行时间： "+(endTime-startTime)+"ms"); 
////						}
////					});
//					if(inqMsg.action.equals("raise") && inqMsg.preBet != inqMsg.curBet) {
//						// 算是一个加注
//						countRaise++;
//					}
//				}
//				
//			}
////			try {
////				countDownLatch.await(213, TimeUnit.MILLISECONDS);
////			} catch (InterruptedException e) {
////				// TODO 自动生成的 catch 块
////				e.printStackTrace();
////				Logger.Log(e.getMessage());
////			}
////			exs.shutdown();
////			try {
////				boolean done = exs.awaitTermination(200, TimeUnit.MILLISECONDS);
////				Logger.Log("reweight done?"+done);
////			} catch (InterruptedException e) {
////				Logger.Log("InterruptedException:"+e.getMessage());
////			}
//		}
	
	
	private void handleNotify(String content) {
		// 圈数加一
		circle++;
		preMsgList.clear();
		preMsgList.addAll(curMsgList);
		curMsgList.clear();
		
		String[] lines = content.split("\\n");
		if(lines == null) {
			return;
		}
		// 从第1行开始,下注的数目要除去最后一行的total pot:
//		int nums = lines.length - 2;
		//               pid      jetton   money    bet      action
		String regex = "(\\d+)\\s(\\d+)\\s(\\d+)\\s(\\d+)\\s(\\w+)";
		Pattern pattern = Pattern.compile(regex);
		for(int i = 1; i < lines.length-1; i++) {
			Matcher m = pattern.matcher(lines[i]);
			if(m.find()) {
				final String pid = m.group(1);
				final String action = m.group(5);
				final String bet = m.group(4);
				if(pid.equals(ID)) {
					myLastAction = myCurAction;
					myCurAction = action;
					try{
						// 记录当前信息
						myPreBet = myCurBet;
						myCurBet = Integer.parseInt(m.group(4));
						myAllJetton = Integer.parseInt(m.group(2));
						myAllMoney = Integer.parseInt(m.group(3));
					}catch(NumberFormatException ex) {
						Logger.Log(ex.getMessage());
					}
					curMsgList.addFirst(new InqNotifyMsg(pid, true, action, 
							myLastAction, myPreBet, myCurBet));
				}else{
					if(aliveOpp.contains(pid) == false) {
						continue;
					}
					Opponent opp = null;
					if(opponents.containsKey(pid)) {
						opp = opponents.get(pid);
					}else{
						// 不应该存在这种情况
						Logger.Log("Notify:"+pid+"不存在！");
						opp = new Opponent();
					}
					opp.ID = pid;
					opp.lastAction = opp.curAction;
					opp.curAction = action;
					try{
						opp.jetton = Integer.parseInt(m.group(2));
						opp.money = Integer.parseInt(m.group(3));
						int curbet = Integer.parseInt(m.group(4));
						opp.preBet = opp.curBet;
						opp.curBet = curbet;
					}catch(NumberFormatException ex) {
						Logger.Log(ex.getMessage());
					}
					// 不包括自己！
					if(action.equals("all_in") && (opp.preBet != opp.curBet)) {
						opp.allinCnt++;
					}else if(action.equals("blind")){
					}else if(action.equals("call")&& (opp.preBet != opp.curBet)){
						opp.callCnt++;
					}else if(action.equals("fold")){
						opp.foldCnt++;
						if(currentState == State.PRE_FLOP) {
							// 翻牌前弃牌
							opp.foldBeforeFlop++;
							foldPreFlop++;
						}
						aliveOpp.remove(pid);
					}else if(action.equals("check")){
						opp.checkCnt++;
					}else if(action.equals("raise")&& (opp.preBet != opp.curBet)) {
						int oppRaise = opp.curBet - opp.preBet;
						// 计算平均值
						opp.averageRaise = (opp.averageRaise*opp.raiseCnt + oppRaise)
								/(opp.raiseCnt+1);
						opp.raiseCnt++;
						if(currentState == State.PRE_FLOP) {
							// 翻牌前加注
							opp.raisePreFlop++;
						}
					}
					opponents.put(pid, opp);
					curMsgList.addFirst(new InqNotifyMsg(pid, opp.isBehindMe, action
							, opp.lastAction, opp.preBet, opp.curBet));
					
				}
			}
		}
		long startTime=System.currentTimeMillis();   //获取开始时间  
		// 记录和更新权重
		recordAndReweight();
		long endTime=System.currentTimeMillis(); //获取结束时间  
		Logger.Log("recordAndReweight运行时间： "+(endTime-startTime)+"ms");
	}
	// 前面有没有人的加注额比平均值大两倍，如果有，=true
	private boolean dangerRaise = false;
	/**
	 * 处理询问消息
	 * @param content
	 */
	private void handleInquire(String content) {
		// 圈数加一
		circle++;
		preMsgList.clear();
		preMsgList.addAll(curMsgList);
		curMsgList.clear();
		
		dangerRaise = false;
		Logger.Log("currentInquire, allive opp nums:"+aliveOpp.size());
		playerCntInInquire = allPlayerNums;
		// 至少需要加大盲注的钱
		leastRaise = bigBlindBet;
		// 所有对手加起来的钱
		int allEnemyMoney = 0;
		minBet = myCurBet = 0;
		int bigBet = 0;
		allinCnt = foldCnt = callCnt = raiseCnt = checkCnt = blindCnt = 0;
		totalPot = 0;
		String[] lines = content.split("\\n");
		if(lines == null) {
			thread.sendMsg(FOLD);
			return;
		}
		// 从第1行开始,下注的数目要除去最后一行的total pot:
		playerCntInInquire = lines.length - 2;
		
		//               pid      jetton   money    bet      action
		String regex = "(\\d+)\\s(\\d+)\\s(\\d+)\\s(\\d+)\\s(\\w+)";
		Pattern pattern = Pattern.compile(regex);
		int linesLength = lines.length-1;
		for(int i = 1; i < linesLength; i++) {
			Matcher m = pattern.matcher(lines[i]);
			if(m.find()) {
//				System.out.println("pid="+m.group(1)+" jetton="+m.group(2)
//						+" money="+m.group(3)+" bet="+m.group(4)+" action="+m.group(5));
				final String pid = m.group(1);
				final String action = m.group(5);
				if(pid.equals(ID)) {
					try{
						// 记录当前信息
						myPreBet = myCurBet;
						myCurBet = Integer.parseInt(m.group(4));
						myAllJetton = Integer.parseInt(m.group(2));
						myAllMoney = Integer.parseInt(m.group(3));
					}catch(NumberFormatException ex) {
						Logger.Log(ex.getMessage());
					}
					myLastAction = myCurAction;
					myCurAction = action;
					curMsgList.addFirst(new InqNotifyMsg(pid, true, action,
							myLastAction, myPreBet, myCurBet));
				}else{
					Opponent opp = null;
					if(opponents.containsKey(pid)) {
						opp = opponents.get(pid);
					}else{
						// 不应该存在这种情况
						Logger.Log("Inquire:"+pid+"不存在！");
						opp = new Opponent();
					}
					opp.ID = pid;
					opp.lastAction = opp.curAction;
					opp.curAction = action;
					boolean stillAlive = aliveOpp.contains(pid);
					try{
						int bet = Integer.parseInt(m.group(4));
						opp.preBet = opp.curBet;
						opp.curBet = bet;
						if(!action.equals("fold")){
							if(bet > bigBet){
								bigBet = bet;
							}
						}
						opp.jetton = Integer.parseInt(m.group(2));
						opp.money = Integer.parseInt(m.group(3));
						allEnemyMoney = allEnemyMoney + opp.jetton + opp.money;
					}catch(NumberFormatException ex) {
						Logger.Log(ex.getMessage());
					}
					// 不包括自己！
					if(action.equals("all_in")) {
						if(stillAlive && (opp.preBet != opp.curBet)){
							opp.allinCnt++;
							allinCnt++;
//							opp.lastAction = opp.curAction;
//							opp.curAction = ALL_IN;
						}
					}else if(action.equals("blind")){
						blindCnt++;
					}else if(action.equals("call")){
						if(stillAlive && (opp.preBet != opp.curBet)){
							opp.callCnt++;
							callCnt++;
//							opp.lastAction = opp.curAction;
//							opp.curAction = CALL;
						}
					}else if(action.equals("fold")){
						if(stillAlive){
							foldCnt++;
							opp.foldCnt++;
							if(currentState == State.PRE_FLOP) {
								// 翻牌前弃牌
								opp.foldBeforeFlop++;
								foldPreFlop++;
							}
//							opp.lastAction = opp.curAction;
//							opp.curAction = FOLD;
							// 弃牌了，去掉
							boolean flag = aliveOpp.remove(pid);
							Logger.Log("remove pid:"+pid+" flag="+flag);
						}
					}else if(action.equals("check")){
						checkCnt++;
						if(stillAlive){
							opp.checkCnt++;
						}
					}else if(action.equals("raise")) {
						if(stillAlive && (opp.preBet != opp.curBet)){
							raiseCnt++;
							int oppRaise = opp.curBet - opp.preBet;
							if(oppRaise > leastRaise) {
								leastRaise = opp.curBet - opp.preBet;
							}
							if(oppRaise >= 2*opp.averageRaise) {
								// 大于两倍
								dangerRaise = true;
							}
							// 计算平均值
							opp.averageRaise = (opp.averageRaise*opp.raiseCnt + oppRaise)
									/(opp.raiseCnt+1);
							opp.raiseCnt++;
							if(currentState == State.PRE_FLOP) {
								// 翻牌前加注
								opp.raisePreFlop++;
							}
//							opp.lastAction = opp.curAction;
//							opp.curAction = RAISE;
						}
					}
					//opponents.remove(pid);
					opponents.put(pid, opp);
					curMsgList.addFirst(new InqNotifyMsg(pid, opp.isBehindMe, action,
							opp.lastAction, opp.preBet, opp.curBet));
				}
			}
		}
		// 当前金钱差距
		moneyGap = myAllJetton + myAllMoney - allEnemyMoney;
		// 计算这轮要下的最小注
		minBet = ((bigBet - myCurBet) > 0 ? (bigBet - myCurBet) : 0);
		Logger.Log("myAllJetton:"+myAllJetton+" allMoney:"+myAllMoney);
		Logger.Log("bigbet="+bigBet+" myCurBet="+myCurBet+" minBet="+minBet);
	    if (myAllJetton < minBet)
	    {
	    	minBet = myAllJetton;
	    }
		curHandPlayerNums = allPlayerNums - foldCnt -1;
		Logger.Log("allPlayerNums:"+allPlayerNums+" foldCnt:"+foldCnt+" curHandPlayerNums:"+curHandPlayerNums);
		
		regex = "total pot: (\\d+)";
		pattern = Pattern.compile(regex);
		Matcher m = pattern.matcher(lines[lines.length-1]);
		if(m.find()) {
//			System.out.println("total pot:"+m.group(1));
			try{
				totalPot = Integer.parseInt(m.group(1));
			}catch(NumberFormatException ex) {
				Logger.Log(ex.getMessage());
			}
		}
		long startTime=System.currentTimeMillis();   //获取开始时间  
		// 记录和更新权重
		recordAndReweight();
		long endTime=System.currentTimeMillis(); //获取结束时间  
		Logger.Log("recordAndReweight运行时间： "+(endTime-startTime)+"ms"); 
		
		try{
			doAction();
		}catch(Exception ex) {
			Logger.Log("doAction err:"+ex.getMessage());
		}
	}
	
	/**
	 * 根据字符串返回相应的枚举变量
	 * @param str
	 * @return
	 */
	private int getPokerValFromStr(final String str) {
		if(str == null || str.length() == 0) {
			return Poker.NONE;
		}
		if(str.equals(DIAMONDS)){
			return Poker.Diamonds;
		}else if(str.equals(SPADES)){
			return Poker.Spades;
		}else if(str.equals(CLUBS)){
			return Poker.Clubs;
		}else if(str.equals(HEARTS)){
			return Poker.Hearts;
		}else if(str.equals("J")){
			return Poker.J;
		}else if(str.equals("Q")){
			return Poker.Q;
		}else if(str.equals("K")){
			return Poker.K;
		}else if(str.equals("A")){
			return Poker.A;
		}else{
			try{
				int val = Integer.parseInt(str);
				return val;
			}catch(NumberFormatException ex) {
				Logger.Log(ex.getMessage());
				return Poker.NONE;
			}
		}
	}
	
	/**
	 * 解析扑克点数和花色并保存
	 * @param content
	 */
	private void parsePoker(String content) {
		String regex = "([A-Z]+?)\\s(\\d{1,2}|[JQKA])";
		Pattern pattern = Pattern.compile(regex);
		Matcher m = pattern.matcher(content);
		int suit;
		int rank;
		while(m.find()) {
			//System.out.println(m.group(1)+":"+m.group(2));
			// group(1)花色  group(2)点数
			suit = getPokerValFromStr(m.group(1));
			rank = getPokerValFromStr(m.group(2));
			shareRank[shareIdx] = rank;
			shareSuit[shareIdx] = suit;
			boardCardVal[shareIdx] = (rank-2) + (suit * 13);
			shareIdx++;
		}
		handEval.parseBoardCard(boardCardVal, shareIdx);
	}
	/**
	 * 处理手牌信息
	 * @param content
	 */
	private void handleHold(String content) {
		currentState = State.PRE_FLOP;
		String regex = "([A-Z]+?)\\s(\\d{1,2}|[JQKA])";
		Pattern pattern = Pattern.compile(regex);
		Matcher m = pattern.matcher(content);
		int suit;
		int rank;
		int idx = 0;
		while(m.find()) {
			//System.out.println(m.group(1)+":"+m.group(2));
			// group(1)花色  group(2)点数
			suit = getPokerValFromStr(m.group(1));
			rank = getPokerValFromStr(m.group(2));
			if(idx == 0) {
				suit1 = suit;
				rank1 = rank;
			}else{
				suit2 = suit;
				rank2 = rank;
			}
			idx++;
		}
		handEval.parsePocketCard(((rank1-2)+suit1*13), ((rank2-2)+suit2*13));
	}
	
	private void handleFlop(String content) {
		// 每进一轮，circle=0
		circle = 0;
		shareIdx = 0;
		parsePoker(content);
		currentState = State.FLOP;
	}
	private void handleTurn(String content) {
		// 每进一轮，circle=0
	    circle = 0;
		parsePoker(content);
		currentState = State.TURN;
	}
	private void handleRiver(String content) {
		// 每进一轮，circle=0
	    circle = 0;
		parsePoker(content);
		currentState = State.RIVER;
	}
	
	private void handleSeat(String content) {
		// TODO:log opp
//		for(Opponent tmpopp : opponents.values()) {
//			Logger.Log(tmpopp.toString());
//		}
		
		circle = 0;
		preMsgList.clear();
		curMsgList.clear();
		if(allPlayerNums > 0) {
			// 计算上一局foldpreflop的百分比
			double prob = (double)foldPreFlop / allPlayerNums;
			probability_play[probPlayIdx++] = 1 - prob;
		}
		if(preAllPlayerNums > 0 && allPlayerNums > 0) {
			if(preAllPlayerNums - allPlayerNums >= 2) {
				// 至少减少至少2个对手以上才重置一次
				preAllPlayerNums = allPlayerNums;
				probPlayIdx = 0;
			}
		}
//		if(!canLiveTillEnd()) {
//			myGameStyle = GameStyle.TIGHT_GAME;
//		}
		/* 切换风格
		double playOdds = getProbabilityPlay();
		if(playOdds <= 0.45) {
			// 进入flop的玩家的比例少，说明赛场是tight风格
			myGameStyle = GameStyle.LOOSE_GAME;
		}else if(playOdds > 0.6) {
			myGameStyle = GameStyle.TIGHT_GAME;
		}else{
			myGameStyle = GameStyle.MODERATE_GAME;
		}
		if(myGameStyle == GameStyle.LOOSE_GAME && !canLiveTillEnd()) {
			myGameStyle = GameStyle.MODERATE_GAME;
		}
		Logger.Log("mygameStyle="+myGameStyle);
		*/
		
		// 每一局开始时先清空之前的统计
		raisePreFlopCnt = raiseFLopCnt = raiseTurnCnt = raiseRiverCnt = 0;
		foldPreFlop = 0;
		awayFromBtn = -1;
		allPlayerNums = 0;
		aliveOpp.clear();
		// 一开始采用紧的打法
		isTight = true;
		myPreBet = myCurBet = 0;
		myLastAction = myCurAction = "";
		
		// 当前排名
		int curRoundRank = 0;
		// 保存对手总的钱数，用来判断排名
		int otherGold[] = new int[7];
		int otherIdx = 0;
		if(content == null) return;
		//        pid    jetton   money
		String regex = "(button:\\s|small blind:\\s||big blind:\\s)?(\\d+)\\s(\\d+)\\s(\\d+)";
		Pattern pattern = Pattern.compile(regex);
		Matcher m = pattern.matcher(content);
		int playerCnt = 0, myPos = 0;
		boolean foundMyPos = false;
		boolean buttonOrBlind = false;
		while(m.find()){
			if(m.group().contains("button") || m.group().contains("blind")){
				//Logger.Log("type="+m.group(1)+" pid="+m.group(2)+" jetton="+m.group(3)+" money="+m.group(4));
				String type = m.group(1);
				String pidStr = m.group(2), jettonStr = m.group(3), moneyStr = m.group(4);
				if(pidStr != null && pidStr.equals(ID)) {
					if(type.contains("button")) {
						awayFromBtn = 0;
						myCurPos = Position.LATE_POS;
						Logger.Log("I am button!awayFromBtn="+awayFromBtn);
					}else if(type.contains("small blind")) {
						awayFromBtn = 1;
						myCurPos = Position.SBLIND_POS;
					}else if(type.contains("big blind")) {
						awayFromBtn = 2;
						myCurPos = Position.BLIND_POS;
					}
					try{
						myAllJetton = Integer.parseInt(jettonStr);
						myAllMoney = Integer.parseInt(moneyStr);
					}catch(NumberFormatException ex) {
						Logger.Log(ex.getMessage());
					}
					foundMyPos = true;
					buttonOrBlind = true;
				}else{
					// 对手
					Opponent opp = null;
					if(opponents.containsKey(pidStr)) {
						opp = opponents.get(pidStr);
					}else{
						opp = new Opponent();
						opponents.put(pidStr, opp);
					}
					opp.ID = pidStr;
					aliveOpp.add(opp.ID);
					if(!foundMyPos) {
						myPos++;
						// 还没找到我,或者我是庄家，说明其他人是我的上家
						opp.isBehindMe = false;
					}else{
						// 找到我了，说明是我的下家
						opp.isBehindMe = true;
					}
					if(awayFromBtn == 0) {
						opp.isBehindMe = false;;
					}
					try{
						int otherJetton = Integer.parseInt(jettonStr);
						int otherMoney = Integer.parseInt(moneyStr);
						opp.jetton = otherJetton;
						opp.money = otherMoney;
						otherGold[otherIdx++] = otherJetton + otherMoney;
					}catch(NumberFormatException ex) {
						Logger.Log(ex.getMessage());
					}
					opp.pos = playerCnt;
					// 清空之前的信息
					Arrays.fill(opp.weight, 1f);;
					opp.lastAction = opp.curAction = "";
					opp.curBet = 0;
					if(type.contains("button")) {
						// 庄家是我的下家
						opp.isBehindMe = true;
					}
					
				}
			}else{
				//Logger.Log("pid="+m.group(2)+" jetton="+m.group(3)+" money="+m.group(4));
				String pidStr = m.group(2), jettonStr = m.group(3), moneyStr = m.group(4);
				if(pidStr != null && pidStr.equals(ID)) {
					try{
						myAllJetton = Integer.parseInt(jettonStr);
						myAllMoney = Integer.parseInt(moneyStr);
					}catch(NumberFormatException ex) {
						Logger.Log(ex.getMessage());
					}
					foundMyPos = true;
				}else{
					Opponent opp = null;
					if(opponents.containsKey(pidStr)) {
						opp = opponents.get(pidStr);
					}else{
						opp = new Opponent();
						opponents.put(pidStr, opp);
					}
					opp.ID = pidStr;
					aliveOpp.add(opp.ID);
					if(!foundMyPos) {
						myPos++;
						// 还没找到我，说明是我的上家
						opp.isBehindMe = false;
					}else{
						// 找到我了，说明是我的下家
						opp.isBehindMe = true;
					}
					if(awayFromBtn == 0) {
						opp.isBehindMe = false;;
					}
					try{
						int otherJetton = Integer.parseInt(jettonStr);
						int otherMoney = Integer.parseInt(moneyStr);
						opp.jetton = otherJetton;
						opp.money = otherMoney;
						otherGold[otherIdx++] = otherJetton + otherMoney;
					}catch(NumberFormatException ex) {
						Logger.Log(ex.getMessage());
					}
					// 清空之前的信息
					//opp.weight.clear();
					Arrays.fill(opp.weight, 1f);
					opp.lastAction = opp.curAction = "";
					opp.curBet = 0;
				}
			}
			playerCnt++;
		}
//		for(int i = 0; i < otherIdx; i++) {
//			if(otherGold[i] > myAllJetton + myAllMoney) {
//				curRoundRank++; // 表示名次降1
//			}
//		}
//		Logger.Log("curRoundRank="+curRoundRank);
		
//		int round = ALL_ROUND_NUM - remainTurn;
//		if(round <= 40) {
//			myGameStyle = GameStyle.MODERATE_GAME;
//		}else{
//			GameStyle style = getGameStyle();
//			Logger.Log("round:"+round+"gameStyle="+style);
//			if(style == GameStyle.LOOSE_GAME) {
//				// 整个赛场是loose的，我变tight
//				myGameStyle = GameStyle.TIGHT_GAME;
//			}else{
//				myGameStyle = GameStyle.LOOSE_GAME;
//			}
//		}
//		if(curRoundRank <= 4) {
//			// 前4名，变保守
//			isTight = true;
//		}else{
//			// 跌到第四以后，反正都输了，激进一点
//			isTight = false;
//		}
//		if(canLiveTillEnd() == false) {
//			isTight = true;
//		}
		Logger.Log("isTight="+isTight);
		remainTurn--;	// 局数	--
		allPlayerNums = playerCnt;
		Logger.Log("myjetton="+myAllJetton+" myMoney="+myAllMoney);
		Logger.Log("allPlayerNums="+allPlayerNums+" myPos="+myPos+" myCurPos="+myCurPos);
		if(preAllPlayerNums == 0) {
			preAllPlayerNums = allPlayerNums;
		}
		
		if(buttonOrBlind) {
			return;
		}
		awayFromBtn = myPos;
		 // 判断玩家位置
		 if (allPlayerNums == 8) {
		      if ((myPos == 3) || (myPos == 4)) {
		            myCurPos = Position.EARLY_POS;
		        }
		        else if ((myPos == 5) || (myPos == 6))
		        {
		        	myCurPos = Position.MID_POS;
		        }
		        else
		        {
		        	myCurPos = Position.LATE_POS;
		        }
		  }else if (allPlayerNums == 7) {
		        if (myPos == 3 || (myPos == 4))
		        {
		        	myCurPos = Position.EARLY_POS;
		        }
		        else if ((myPos == 5)){
		        	myCurPos = Position.MID_POS;
		        }
		        else
		        {
		        	myCurPos = Position.LATE_POS;
		        }
		    }else if (allPlayerNums == 6)
		    {
		        if ((myPos == 3)) {
		        	myCurPos = Position.EARLY_POS;
		        }
		        else if ((myPos == 4)) {
		        	myCurPos = Position.MID_POS;
		        }
		        else {
		        	myCurPos = Position.LATE_POS;
		        }
		    }else if (allPlayerNums == 5) {
		        if ((myPos == 3))
		        {
		        	myCurPos = Position.EARLY_POS;
		        }
		        else
		        {
		        	myCurPos = Position.MID_POS;
		        }
		    }else if (allPlayerNums == 4)
		    {
		    	myCurPos = Position.EARLY_POS;
		    }else{
		    	myCurPos = Position.LATE_POS;
		    }
	}
	private void handleBlind(String content) {
		bigBlindBet = 0;
		String regex = "(\\d+):\\s(\\d+)";
		Pattern pattern = Pattern.compile(regex);
		Matcher m = pattern.matcher(content);
		while(m.find()) {
			String bet = m.group(2);
			try{
				int num = Integer.parseInt(bet);
				if(num > bigBlindBet) {
					bigBlindBet = num;
				}
			}catch(NumberFormatException ex) {
				Logger.Log(ex.getMessage());
			}
		}
	}
	/**
	 * 预处理消息
	 * @param msg
	 */
	private void preHandleMsg(String type, String body) {
			// Logger.Log("type:"+type+" body:"+body);
			if(type == null) return;
			type = type.trim();
			if(type.equals("notify")){
				long startTime = System.currentTimeMillis(); //获取结束时间  
				handleNotify(body);
				long endTime = System.currentTimeMillis(); //获取结束时间  
				Logger.Log("handleNotify运行时间： "+(endTime-startTime)+"ms"); 
			}else if(type.equals("seat")) {
				// 座次信息
				handleSeat(body);
			}else if(type.equals("blind")){
				// 盲注消息
				handleBlind(body);
			}else if(type.equals("hold")){
				// 手牌消息
				handleHold(body);
			}else if(type.equals("inquire")){
				long startTime = System.currentTimeMillis(); //获取结束时间  
				// 询问消息
				handleInquire(body);
				long endTime = System.currentTimeMillis(); //获取结束时间  
				Logger.Log("handleInquire运行时间： "+(endTime-startTime)+"ms"); 
			}else if(type.equals("flop")){
				// 三张公牌消息
				handleFlop(body);
			}else if(type.equals("turn")){
				// 一张转牌消息
				handleTurn(body);
			}else if(type.equals("river")){
				// 最后一张河牌消息
				handleRiver(body);
			}else if(type.equals("showdown")){
				// 摊牌消息
			}else if(type.equals("pot-win")){
				// 彩池分配消息消息
			}else if(type.equals("game-over")){
//				threadPool.shutdownNow();
			}
		
	}
	public void play() {
		Logger.Log("begin to play");
		thread = new SocketThread(serverIp, serverPort, localIp, localPort, ID);
		thread.setCallBack(this);
		thread.setPriority(Thread.NORM_PRIORITY+1);
		thread.start();
		handEval = new KRHandEvaluator();
		handEval.initData();
		
	}
	
	@Override
	public void receiveMsg(String type, String body) {
		preHandleMsg(type, body);		
	}
}