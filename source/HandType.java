class HandType {
	public static final int HighCard = 0,	// 高牌
			Pair = 1,				// 一对
			TwoPair = 2,				// 两队
			Trips = 3,		// 三条
			Straight = 4,				// 顺子
			Flush = 5, FullHouse = 6, FourOfAKind = 7, StraightFlush = 8,
			
			// 以下表示两张牌的牌力
			VERY_STRENGTH = 0,       // 超强牌
		    STRENGTH = 1,            // 强牌
		    NORMAL = 2,              // 普通牌
		    SPECULARION = 3,             // 投机牌
		    MIXED = 4,                  // 混合牌
		    STEAL_CARD = 5,             // 诈唬牌
		    FOLD_CARD = 6;            // 丢弃牌

}
