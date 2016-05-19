

/**
 * 提供对象建模的方法供调用
 * @author jenson
 *
 */
class OpponentModel {
	/*
	 * Default frequencies[bets_tocall][action]
	 */
	public static final double d[][] = {
		{0, 0.5, 0.5},
		{0.5, 0.3, 0.2},
		{0.7, 0.2, 0.1}
	};
	private static final String CALL = "call";		// 跟住
	private static final String CHECK = "check";		// 让牌
	private static final String RAISE = "raise";		// 加注
	private static final String ALL_IN = "all_in";		// 全压
	private static final String FOLD = "fold";			// 弃牌
	/**
	 * update the opponent model given an observed action
	 * @param player
	 * @param action
	 * @param isSpecific is this model specific or general
	 */
	public static void HandleAction(Opponent player, String action, boolean isSpecific)
	{
		
	}

}
