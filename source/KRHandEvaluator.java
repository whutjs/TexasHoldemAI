

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;


// Much of this code is derived from poker.eval (look for it on sourceforge.net).
// This library is covered by the LGPL Gnu license. See http://www.gnu.org/copyleft/lesser.html 
// for more information on this license.

// This code is a very fast, native C# Texas Holdem hand evaluator (containing no interop or unsafe code). 
// This code can enumarate 35 million 5 card hands per second and 29 million 7 card hands per second on my desktop machine.
// That's not nearly as fast as the heavily macro-ed poker.eval C library. However, this implementation is
// in roughly the same ballpark for speed and is quite usable in C#.

// The speed ups are mostly table driven. That means that there are several very large tables included in this file. 
// The code is divided up into several files they are:
//      HandEvaluator.cs - base hand evaluator
//      HandIterator.cs - methods that support IEnumerable and methods that validate the hand evaluator
//      HandAnalysis.cs - methods to aid in analysis of Texas Holdem Hands.

// Written (ported) by Keith Rule - Sept 2005


class KRHandEvaluator
{
	 public  final int NumberOfCards = 52;
     
     public  final int NCardsWJoker = 53;
     
     private  final int HANDTYPE_SHIFT = 24;
     
     private  final int TOP_CARD_SHIFT = 16;
     
     private  final long TOP_CARD_MASK = 0x000F0000;
     
     private  final int SECOND_CARD_SHIFT = 12;
     
     private  final long SECOND_CARD_MASK = 0x0000F000;
     
     private  final int THIRD_CARD_SHIFT = 8;
     
     private  final int FOURTH_CARD_SHIFT = 4;
     
     private  final int FIFTH_CARD_SHIFT = 0;
     
     private  final long FIFTH_CARD_MASK = 0x0000000F;
     
     private  final int CARD_WIDTH = 4;
     
     private  final long CARD_MASK = 0x0F;
     
     
     private  final long HANDTYPE_VALUE_STRAIGHTFLUSH = (((long)HandType.StraightFlush) << HANDTYPE_SHIFT);
     private  final long HANDTYPE_VALUE_STRAIGHT = (((long)HandType.Straight) << HANDTYPE_SHIFT);
     private  final long HANDTYPE_VALUE_FLUSH = (((long)HandType.Flush) << HANDTYPE_SHIFT);
     private  final long HANDTYPE_VALUE_FULLHOUSE = (((long)HandType.FullHouse) << HANDTYPE_SHIFT);
     private  final long HANDTYPE_VALUE_FOUR_OF_A_KIND = (((long)HandType.FourOfAKind) << HANDTYPE_SHIFT);
     private  final long HANDTYPE_VALUE_TRIPS = (((long)HandType.Trips) << HANDTYPE_SHIFT);
     private  final long HANDTYPE_VALUE_TWOPAIR = (((long)HandType.TwoPair) << HANDTYPE_SHIFT);
     private  final long HANDTYPE_VALUE_PAIR = (((long)HandType.Pair) << HANDTYPE_SHIFT);
     private  final long HANDTYPE_VALUE_HIGHCARD = (((long)HandType.HighCard) << HANDTYPE_SHIFT);
     
     public  final int SPADE_OFFSET = 13 * Poker.Spades;
     public  final int CLUB_OFFSET = 13 * Poker.Clubs;
     public  final int DIAMOND_OFFSET = 13 * Poker.Diamonds;
     public  final int HEART_OFFSET = 13 * Poker.Hearts;
     // 当前底牌的值
     private long myPocketCardMask = 0;
     private long boardCardMask = 0;
     
     private LookupTable lktable;
     public KRHandEvaluator() {
    	 lktable = new LookupTable();
     }
     
     /**
      * 将底牌转化为mask
      * @param card1 = rank + (suit * 13);
      * @param card2 = rank + (suit * 13);
      */
     public void parsePocketCard(int card1, int card2) {
    	 long handmask = (1L << card1);
    	 handmask |= (1L << card2);
    	 myPocketCardMask = handmask;
     }
     
     public void parseBoardCard(int card[], int num) {
    	 long handmask = 0;
    	 for(int i = 0; i < num; i++) {
    		 handmask |= (1L << card[i]);
    	 }
    	 boardCardMask = handmask;
     }
     
     // 根据long转化为unsignedInt
     private int getUnsignedInt(long lval) {
     	return (int)(lval & 0x0FFFFFFFFl);
     }
     
     public boolean initOK() {
    	 return lktable.initOk;
     }
     
     public void initData() {
    	 if(lktable.initOk == false) {
    		 lktable.initData();
    	 }
     }
     
     
     
     // The value of the current had. This value allows hands to be 
     // compared using a normal arithmitic compare function.
     private long handval;
     	
     	/**
     	 * 得到公牌有多少个花色相同
     	 */
	     public int getBoardSuitedNum() {
	    	 	long mask = boardCardMask;
	    	 	long sc = CardMask(mask, Poker.Clubs);
	            long sd = CardMask(mask, Poker.Diamonds);
	            long sh = CardMask(mask, Poker.Hearts);
	            long ss = CardMask(mask, Poker.Spades);
	            int maxcount = 0;
	            int cnt[] = new int[4];
	            cnt[0] = BitCount(sc);
	            cnt[1] = BitCount(sd);
	            cnt[2] = BitCount(sh);
	            cnt[3] = BitCount(ss);
	            for(int i = 0; i < 4; i++) {
	            	if(cnt[i] > maxcount){
	            		maxcount = cnt[i];
	            	}
	            }
	            return maxcount;
	     }
       // This function returns true if the cards in the hand are all one suit
        public boolean IsSuited(long mask)
        {
            int cards = BitCount(mask);
           
            long sc = CardMask(mask, Poker.Clubs);
            long sd = CardMask(mask, Poker.Diamonds);
            long sh = CardMask(mask, Poker.Hearts);
            long ss = CardMask(mask, Poker.Spades);

            return  BitCount(sc) == cards || BitCount(sd) == cards ||
                    BitCount(sh) == cards || BitCount(ss) == cards;
        }

        
        /**
         * Returns true if the cards in the two card hand are connected.
         * @param mask the hand to check
         * @return true of all of the cards are next to each other.
         */
        public boolean IsConnected(long mask)
        {
            return GapCount(mask) == 0;
        }
        
        // Given a card value, returns it's rank
        public  int CardRank(int card)
        {
            return card % 13;
        }
        
        // Given a card value, returns it's suit
        public  int CardSuit(int card)
        {
            return card / 13;
        }
        // Counts the number of empty space between adjacent cards. 0 means connected, 1 means a gap
        // of one, 2 means a gap of two and 3 means a gap of three.
        public  int GapCount(long mask)
        {
            int start, end;

            if (BitCount(mask) != 2) return -1;

            long bf = CardMask(mask, Poker.Clubs) |
                        CardMask(mask, Poker.Diamonds) |
                        CardMask(mask, Poker.Hearts) |
                        CardMask(mask, Poker.Spades);

            if (BitCount(bf) != 2) return -1;

            for (start = 12; start >= 0; start--)
            {
                if ((bf & (1l << start)) != 0)
                    break;
            }

            for (end = start - 1; end >= 0; end--)
            {
                if ((bf & (1l << end)) != 0)
                    break;
            }

            // Handle wrap
            if (start == 12 && end == 0) return 0;
            if (start == 12 && end == 1) return 1;
            if (start == 12 && end == 2) return 2;
            if (start == 12 && end == 3) return 3;

            return start-end-1;
        }
        
        
        /**
         * Enables a foreach command to enumerate all possible ncard hands.
         * @param shared A bitfield containing the cards that must be in the enumerated hands
         * @param dead A bitfield containing the cards that must not be in the enumerated hands
         * @param numberOfCards the number of cards in the hand (must be between 1 and 7)
         * @return
         */
        private List<Long> Hands(final long shared, long dead, final int numberOfCards)
        {
        	List<Long> res = new LinkedList<Long>();
            int _i1, _i2, _i3, _i4, _i5, _i6, _i7, length;
            long _card1, _card2, _card3, _card4, _card5, _card6, _card7;
            long _n2, _n3, _n4, _n5, _n6;

            dead |= shared;
//            Logger.Log("in Hands:shared="+shared+" dead="+dead+" numberOfCards:"+numberOfCards);
//            Logger.Log("numberOfCards - BitCount(shared):"+(numberOfCards - BitCount(shared)));
            switch (numberOfCards - BitCount(shared))
            {
                case 7:
                    for (_i1 = NumberOfCards - 1; _i1 >= 0; _i1--)
                    {
                        _card1 = lktable.CardMasksTable[_i1];
                        if ((dead & _card1) != 0) continue;
                        for (_i2 = _i1 - 1; _i2 >= 0; _i2--)
                        {
                            _card2 = lktable.CardMasksTable[_i2];
                            if ((dead & _card2) != 0) continue;
                            _n2 = _card1 | _card2;
                            for (_i3 = _i2 - 1; _i3 >= 0; _i3--)
                            {
                                _card3 = lktable.CardMasksTable[_i3];
                                if ((dead & _card3) != 0) continue;
                                _n3 = _n2 | _card3;
                                for (_i4 = _i3 - 1; _i4 >= 0; _i4--)
                                {
                                    _card4 = lktable.CardMasksTable[_i4];
                                    if ((dead & _card4) != 0) continue;
                                    _n4 = _n3 | _card4;
                                    for (_i5 = _i4 - 1; _i5 >= 0; _i5--)
                                    {
                                        _card5 = lktable.CardMasksTable[_i5];
                                        if ((dead & _card5) != 0) continue;
                                        _n5 = _n4 | _card5;
                                        for (_i6 = _i5 - 1; _i6 >= 0; _i6--)
                                        {
                                            _card6 = lktable.CardMasksTable[_i6];
                                            if ((dead & _card6) != 0) continue;
                                            _n6 = _n5 | _card6;
                                            for (_i7 = _i6 - 1; _i7 >= 0; _i7--)
                                            {
                                                _card7 = lktable.CardMasksTable[_i7];
                                                if ((dead & _card7) != 0) continue;
                                                long cal = _n6 | _card7 | shared;
//                                                res.add(ulongToUInt(cal));
                                                res.add(cal);
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    break;
                case 6:
                    for (_i1 = NumberOfCards - 1; _i1 >= 0; _i1--)
                    {
                        _card1 = lktable.CardMasksTable[_i1];
                        if ((dead & _card1) != 0) continue;
                        for (_i2 = _i1 - 1; _i2 >= 0; _i2--)
                        {
                            _card2 = lktable.CardMasksTable[_i2];
                            if ((dead & _card2) != 0) continue;
                            _n2 = _card1 | _card2;
                            for (_i3 = _i2 - 1; _i3 >= 0; _i3--)
                            {
                                _card3 = lktable.CardMasksTable[_i3];
                                if ((dead & _card3) != 0) continue;
                                _n3 = _n2 | _card3;
                                for (_i4 = _i3 - 1; _i4 >= 0; _i4--)
                                {
                                    _card4 = lktable.CardMasksTable[_i4];
                                    if ((dead & _card4) != 0) continue;
                                    _n4 = _n3 | _card4;
                                    for (_i5 = _i4 - 1; _i5 >= 0; _i5--)
                                    {
                                        _card5 = lktable.CardMasksTable[_i5];
                                        if ((dead & _card5) != 0) continue;
                                        _n5 = _n4 | _card5;
                                        for (_i6 = _i5 - 1; _i6 >= 0; _i6--)
                                        {
                                            _card6 = lktable.CardMasksTable[_i6];
                                            if ((dead & _card6) != 0)
                                                continue;
                                            long cal =  _n5 | _card6 | shared ;
//                                            res.add(ulongToUInt(cal));
                                            res.add(cal);
                                        }
                                    }
                                }
                            }
                        }
                    }
                    break;
                case 5:
                    for (_i1 = NumberOfCards - 1; _i1 >= 0; _i1--)
                    {
                        _card1 = lktable.CardMasksTable[_i1];
                        if ((dead & _card1) != 0) continue;
                        for (_i2 = _i1 - 1; _i2 >= 0; _i2--)
                        {
                            _card2 = lktable.CardMasksTable[_i2];
                            if ((dead & _card2) != 0) continue;
                            _n2 = _card1 | _card2;
                            for (_i3 = _i2 - 1; _i3 >= 0; _i3--)
                            {
                                _card3 = lktable.CardMasksTable[_i3];
                                if ((dead & _card3) != 0) continue;
                                _n3 = _n2 | _card3;
                                for (_i4 = _i3 - 1; _i4 >= 0; _i4--)
                                {
                                    _card4 = lktable.CardMasksTable[_i4];
                                    if ((dead & _card4) != 0) continue;
                                    _n4 = _n3 | _card4;
                                    for (_i5 = _i4 - 1; _i5 >= 0; _i5--)
                                    {
                                        _card5 = lktable.CardMasksTable[_i5];
                                        if ((dead & _card5) != 0) continue;
                                        long cal = _n4 | _card5 | shared;
//                                        res.add(ulongToUInt(cal));
                                        res.add(cal);
                                    }
                                }
                            }
                        }
                    }
                    break;
                case 4:
                    for (_i1 = NumberOfCards - 1; _i1 >= 0; _i1--)
                    {
                        _card1 = lktable.CardMasksTable[_i1];
                        if ((dead & _card1) != 0) continue;
                        for (_i2 = _i1 - 1; _i2 >= 0; _i2--)
                        {
                            _card2 = lktable.CardMasksTable[_i2];
                            if ((dead & _card2) != 0) continue;
                            _n2 = _card1 | _card2;
                            for (_i3 = _i2 - 1; _i3 >= 0; _i3--)
                            {
                                _card3 = lktable.CardMasksTable[_i3];
                                if ((dead & _card3) != 0) continue;
                                _n3 = _n2 | _card3;
                                for (_i4 = _i3 - 1; _i4 >= 0; _i4--)
                                {
                                    _card4 = lktable.CardMasksTable[_i4];
                                    if ((dead & _card4) != 0) continue;
                                    long cal =  _n3 | _card4 | shared;
//                                    res.add(ulongToUInt(cal));
                                    res.add(cal);
                                }
                            }
                        }
                    }

                    break;
                case 3:
                    for (_i1 = NumberOfCards - 1; _i1 >= 0; _i1--)
                    {
                        _card1 = lktable.CardMasksTable[_i1];
                        if ((dead & _card1) != 0) continue;
                        for (_i2 = _i1 - 1; _i2 >= 0; _i2--)
                        {
                            _card2 = lktable.CardMasksTable[_i2];
                            if ((dead & _card2) != 0) continue;
                            _n2 = _card1 | _card2;
                            for (_i3 = _i2 - 1; _i3 >= 0; _i3--)
                            {
                                _card3 = lktable.CardMasksTable[_i3];
                                if ((dead & _card3) != 0) continue;
                                long cal = _n2 | _card3 | shared;
//                                res.add(ulongToUInt(cal));
                                res.add(cal);
                            }
                        }
                    }
                    break;
                case 2:
                    length = lktable.TwoCardTable.length;
                    for (_i1 = 0; _i1 < length; _i1++)
                    {
                        _card1 = lktable.TwoCardTable[_i1];
                        if ((dead & _card1) != 0) continue;
                        long cal = _card1 | shared;
//                        res.add(ulongToUInt(cal));
                        res.add(cal);
                    }
                    break;
                case 1:
                    length = lktable.CardMasksTable.length;
                    for (_i1 = 0; _i1 < length; _i1++)
                    {
                        _card1 = lktable.CardMasksTable[_i1];
                        if ((dead & _card1) != 0) continue;
                        long cal =  _card1 | shared;
//                        res.add(ulongToUInt(cal));
                        res.add(cal);
                    }
                    break;
                case 0:
                	 long cal =  shared;
//                     res.add(ulongToUInt(cal));
                     res.add(cal);
                    break;
                default:
                	res.add( 0L);
                    break;
            }
            return res;
        }
        
        // Internal function used by HandPotential.
        private void HandPotentialOpp(long ourcards, long board, long oppcards, int index,  int[][] HP)
        {
            int ahead = 2;
            int tied = 1;
            int behind = 0;
            long dead_cards = ourcards | board | oppcards;
            long ourbest, oppbest;
            List<Long> handsList = Hands(0L, dead_cards, 7-BitCount(ourcards | board));
            for(long handmask : handsList) 
            {
                ourbest = Evaluate(ourcards | board | handmask, 7);
                oppbest = Evaluate(oppcards | board | handmask, 7);
                if (ourbest > oppbest)
                    HP[index][ahead]++;
                else if (ourbest == oppbest)
                    HP[index][tied]++;
                else
                    HP[index][behind]++;
            }
        }
        
        /**
         * 计算EHS
         * @return [0] = ppot, [1] = strength
         */
        public double[] calEHS() {
        	return fastHandPotential(myPocketCardMask, boardCardMask);
        }
        
        // Returns the positive and negative potential of the current hand. This funciton
        // is described in Aaron Davidson's masters thesis (davidson.msc.pdf).      
        private double[] HandPotential(long pocket, long board)
        {
            int ahead = 2;
            int tied = 1;
            int behind = 0;
            double ppot = 0, npot = 0;
            int[][] HP = new int[3][3];
            int[] HPTotal = new int[3];
            int cards = BitCount(pocket | board);
            double mult = (cards == 5 ? 990.0 : 45.0);

            // Initialize
            for (int i = 0; i < 3; i++)
            {
                HPTotal[i] = 0;
                for (int j = 0; j < 3; j++)
                {
                    HP[i][j] = 0;
                }
            }

            // Rank our hand
            long arg1 = pocket | board;
            int arg2 = BitCount(pocket | board);
            long ourrank = Evaluate(arg1, arg2);
            // Mark known cards as dead.
            long dead_cards = pocket | board;
            
            // Iterate through all possible opponent pocket cards
            List<Long> res = Hands(0L, dead_cards, 2);
            for(long oppPocket : res) {
            	// 枚举对手的两张牌
                // Note Current State
                long opprank = Evaluate(oppPocket | board, BitCount(oppPocket | board));
                if (ourrank > opprank)
                {
                    HandPotentialOpp(pocket, board, oppPocket, ahead,  HP);
                    HPTotal[ahead]++;
                }
                else if (ourrank == opprank)
                {
                    HandPotentialOpp(pocket, board, oppPocket, tied,  HP);
                    HPTotal[tied]++;
                }
                else
                {
                    HandPotentialOpp(pocket, board, oppPocket, behind,  HP);
                    HPTotal[behind]++;
                }
            }
            double den1 = (mult * (HPTotal[behind] + (HPTotal[tied] / 2.0)));
            double den2 = (mult * (HPTotal[ahead] + (HPTotal[tied] / 2.0)));
            if (den1 > 0)
                ppot = (HP[behind][ahead] + (HP[behind][tied] / 2) + (HP[tied][ahead] / 2)) / (double)den1;
            else
                ppot = 0;
            if (den2 > 0)
                npot = (HP[ahead][behind] + (HP[ahead][tied] / 2) + (HP[tied][behind] / 2)) / (double)den2;
            else
                npot = 0;
            double strength = (HPTotal[ahead] + 0.5f * HPTotal[tied])  / 
    				(HPTotal[tied] + HPTotal[ahead] + HPTotal[behind]);
//    		double ehs = strength + (1-strength)*ppot;
//    		Logger.Log("ehs="+ehs+" strength="+strength+" ppot="+ppot+" strength="+strength);
    		double result[] = new double[3];
    		result[0] = ppot;
    		result[1] = strength;
//    		result[2] = strength;
    		return result;
        }
        
       
        /**
         * This is a fast way to look up the index mask. 
         * @param index index of mask
         * @return
         */
        public long Mask(int index)
        {
            return lktable.CardMasksTable[index];
        }

        
        public long HandType(long handValue)
        {
            return (handValue >>> HANDTYPE_SHIFT);
        }

        // FXCop complained about this unused private method so I
        // commented it out for now.
        //
        //private  long Cards(long handValue)
        //{
        //    return (handValue & CARD_MASK);
        //}

        
        public  long TopCard(long hv)
        {
            return ((hv >>> TOP_CARD_SHIFT) & CARD_MASK);
        }

        
        private  long SECOND_CARD(long hv)
        {
            return (((hv) >>> SECOND_CARD_SHIFT) & CARD_MASK);
        }

        
        private  long THIRD_CARD(long hv)
        {
            return (((hv) >>> THIRD_CARD_SHIFT) & CARD_MASK);
        }

        
        private  long FOURTH_CARD(long hv)
        {
            return (((hv) >>> FOURTH_CARD_SHIFT) & CARD_MASK);
        }

        
        private  long FIFTH_CARD(long hv)
        {
            return (((hv) >>> FIFTH_CARD_SHIFT) & CARD_MASK);
        }

        
        private  long HANDTYPE_VALUE(int ht)
        {
            return (((long)ht) << HANDTYPE_SHIFT);
        }

        //
        //private  long HANDVALUE2HANDTYPE(long hv)
        //{
        //    return (((long)hv) >>> HANDTYPE_SHIFT) & 0xf;
        //}

        
        private  long TOP_CARD_VALUE(long c)
        {
            return ((c) << TOP_CARD_SHIFT);
        }

        
        private  long SECOND_CARD_VALUE(long c)
        {
            return ((c) << SECOND_CARD_SHIFT);
        }

        
        private  long THIRD_CARD_VALUE(long c)
        {
            return ((c) << THIRD_CARD_SHIFT);
        }

        // FXCop complained about these unused private methods so I 
        // commented them out until I need them.
        //
        //private  long FOURTH_CARD_VALUE(long c)
        //{
        //    return ((c) << FOURTH_CARD_SHIFT);
        //}

        //
        //private  long FIFTH_CARD_VALUE(long c)
        //{
        //    return ((c) << FIFTH_CARD_SHIFT);
        //}
        
        private long CardMask(long cards, int suit)
        {
            return (long)((cards >>> (13 * suit)) & 0x1fffl);
        }
        
        /**
         * 返回公牌的牌型
         * @return
         */
        public int getBoardCardType()
        {
        	return EvaluateType(boardCardMask);
        }
        /**
         * 根据当前底牌和公牌返回是什么类型的牌
         * @return HandType
         */
        public int getMyHandType() {
        	long CurAllCardMask = myPocketCardMask | boardCardMask;
        	return EvaluateType(CurAllCardMask);
        }
        /**
         * Evaluates the card mask and returns the type of hand it is. This function is
         * faster (but provides less information) than Evaluate or Evaluate.
         * @param mask card mask
         * @return HandType value that describes the rank of the hand
         */
        private int EvaluateType(long mask)
        {
            return EvaluateType(mask, BitCount(mask));
        }

       
        /**
         *  This function is faster (but provides less information) than Evaluate or Evaluate.
         * @param mask card mask
         * @param cards number of cards in mask
         * @return HandType enum that describes the rank of the hand
         */
        public  int EvaluateType(long mask, int cards)
        {
            int is_st_or_fl = HandType.HighCard;

            long ss = ulongToUInt((mask >>> (SPADE_OFFSET)) & 0x1fffL);
            long sc = ulongToUInt((mask >>> (CLUB_OFFSET)) & 0x1fffL);
            long sd = ulongToUInt((mask >>> (DIAMOND_OFFSET)) & 0x1fffL);
            long sh = ulongToUInt((mask >>> (HEART_OFFSET)) & 0x1fffL);

            long ranks = sc | sd | sh | ss;
            long rankinfo = lktable.nBitsAndStrTable[(int)ranks];
            long n_dups = ulongToUInt(cards - (getUnsignedInt(rankinfo >>> 2)));

            if ((rankinfo & 0x01) != 0)
            {
                if ((rankinfo & 0x02) != 0)
                    is_st_or_fl = HandType.Straight;

                long t = ulongToUInt(lktable.nBitsAndStrTable[(int)ss] | lktable.nBitsAndStrTable[(int)sc] | lktable.nBitsAndStrTable[(int)sd] | lktable.nBitsAndStrTable[(int)sh]);

                if ((t & 0x01) != 0)
                {
                    if ((t & 0x02) != 0)
                        return HandType.StraightFlush;
                    else
                        is_st_or_fl = HandType.Flush;
                }

                if (is_st_or_fl != 0 && n_dups < 3)
                    return is_st_or_fl;
            }

            switch ((int)n_dups)
            {
                case 0:
                    return HandType.HighCard;
                case 1:
                    return HandType.Pair;
                case 2:
                    return ((ranks ^ (sc ^ sd ^ sh ^ ss)) != 0) ? HandType.TwoPair : HandType.Trips;
                default:
                    if (((sc & sd) & (sh & ss)) != 0) return HandType.FourOfAKind;
                    else if ((((sc & sd) | (sh & ss)) & ((sc & sh) | (sd & ss))) != 0) return HandType.FullHouse;
                    else if (is_st_or_fl != 0) return is_st_or_fl;
                    else return HandType.TwoPair;
            }
        }

       
       
        /**
         * Evaluates a hand (passed as a hand mask) and returns a hand value.
         * A hand value can be compared against another hand value to
         *  determine which has the higher value.
         * @param cards hand mask
         * @return Hand Value bit field
         */
        private long Evaluate(long cards)
        {
            return Evaluate(cards, BitCount(cards));
        }
        
        private long ulongToUInt(long cal) {
            int tmp = (int) (cal & 0x0FFFFFFFFl);
         	long tmpres = tmp;
         	if(tmp < 0) {
         		// 小于0要记得转换
         		tmpres = tmp & 0x0FFFFFFFFl;
         	}
         	return tmpres;
        }
        /**
         * Evaluates a hand (passed as a hand mask) and returns a hand value.
         * A hand value can be compared against another hand value to
         * determine which has the higher value.
         * @param cards hand mask
         * @param numberOfCards number of cards in the hand
         * @return
         */
        private long Evaluate(final long cards, final int numberOfCards)
        {
            long retval = 0, four_mask, three_mask, two_mask;

            // Seperate out by suit
            long sc = ulongToUInt((cards >>> (CLUB_OFFSET)) & 0x1fffL);
            long sd = ulongToUInt((cards >>> (DIAMOND_OFFSET)) & 0x1fffL);
            long sh = ulongToUInt((cards >>> (HEART_OFFSET)) & 0x1fffL);
            long ss = ulongToUInt((cards >>> (SPADE_OFFSET)) & 0x1fffL);
            long ranks = sc | sd | sh | ss;
            long n_ranks = lktable.nBitsTable[(int)ranks];
            long n_dups = ((long)(numberOfCards - n_ranks));
            /* Check for straight, flush, or straight flush, and return if we can
               determine immediately that this is the best possible hand 
            */
            if (n_ranks >= 5)
            {
                if (lktable.nBitsTable[getUnsignedInt(ss)] >= 5)
                {
                    if (lktable.straightTable[getUnsignedInt(ss)] != 0)
                        return HANDTYPE_VALUE_STRAIGHTFLUSH + ulongToUInt(lktable.straightTable[getUnsignedInt(ss)] << TOP_CARD_SHIFT);
                    else
                        retval = HANDTYPE_VALUE_FLUSH + lktable.topFiveCardsTable[getUnsignedInt(ss)];
                }
                else if (lktable.nBitsTable[getUnsignedInt(sc)] >= 5)
                {
                    if (lktable.straightTable[getUnsignedInt(sc)] != 0)
                        return HANDTYPE_VALUE_STRAIGHTFLUSH + ulongToUInt(lktable.straightTable[getUnsignedInt(sc)] << TOP_CARD_SHIFT);
                    else
                        retval = HANDTYPE_VALUE_FLUSH + lktable.topFiveCardsTable[getUnsignedInt(sc)];
                }
                else if (lktable.nBitsTable[getUnsignedInt(sd)] >= 5)
                {
                    if (lktable.straightTable[getUnsignedInt(sd)] != 0)
                        return HANDTYPE_VALUE_STRAIGHTFLUSH + ulongToUInt(lktable.straightTable[getUnsignedInt(sd)] << TOP_CARD_SHIFT);
                    else
                        retval = HANDTYPE_VALUE_FLUSH + lktable.topFiveCardsTable[getUnsignedInt(sd)];
                }
                else if (lktable.nBitsTable[getUnsignedInt(sh)] >= 5)
                {
                    if (lktable.straightTable[getUnsignedInt(sh)] != 0)
                        return HANDTYPE_VALUE_STRAIGHTFLUSH + ulongToUInt(lktable.straightTable[getUnsignedInt(sh)] << TOP_CARD_SHIFT);
                    else
                        retval = HANDTYPE_VALUE_FLUSH + lktable.topFiveCardsTable[getUnsignedInt(sh)];
                }
                else
                {
                    long st = lktable.straightTable[getUnsignedInt(ranks)];
                    if (st != 0)
                        retval = HANDTYPE_VALUE_STRAIGHT + (st << TOP_CARD_SHIFT);
                };

                /* 
                   Another win -- if there can't be a FH/Quads (n_dups < 3), 
                   which is true most of the time when there is a made hand, then if we've
                   found a five card hand, just return.  This skips the whole process of
                   computing two_mask/three_mask/etc.
                */
                if (retval != 0 && n_dups < 3)
                    return ulongToUInt(retval);
            }

            /*
             * By the time we're here, either: 
               1) there's no five-card hand possible (flush or straight), or
               2) there's a flush or straight, but we know that there are enough
                  duplicates to make a full house / quads possible.  
             */
            switch ((int)n_dups)
            {
                case 0:
                    /* It's a no-pair hand */
                    return HANDTYPE_VALUE_HIGHCARD + lktable.topFiveCardsTable[(int)ranks];
                case 1:
                    {
                        /* It's a one-pair hand */
                        long t, kickers;

                        two_mask = ranks ^ (sc ^ sd ^ sh ^ ss);

                        retval = ulongToUInt(HANDTYPE_VALUE_PAIR + (lktable.topCardTable[getUnsignedInt(two_mask)] << TOP_CARD_SHIFT));
                        t = ranks ^ two_mask;      /* Only one bit set in two_mask */
                        /* Get the top five cards in what is left, drop all but the top three 
                         * cards, and shift them by one to get the three desired kickers */
                        kickers = (lktable.topFiveCardsTable[getUnsignedInt(t)] >>> CARD_WIDTH) & ~FIFTH_CARD_MASK;
                        retval += kickers;
                        return retval;
                    }

                case 2:
                    /* Either two pair or trips */
                    two_mask = ranks ^ (sc ^ sd ^ sh ^ ss);
//                    Logger.Log("two_mask="+two_mask);
                    if (two_mask != 0)
                    {
                        long t = ranks ^ two_mask; /* Exactly two bits set in two_mask */
                        retval = ulongToUInt((HANDTYPE_VALUE_TWOPAIR
                            + (lktable.topFiveCardsTable[getUnsignedInt(two_mask)]
                            & (TOP_CARD_MASK | SECOND_CARD_MASK))
                            + (lktable.topCardTable[getUnsignedInt(t)] << THIRD_CARD_SHIFT)));

                        return retval;
                    }
                    else
                    {
                        long t, second;
                        three_mask = ((sc & sd) | (sh & ss)) & ((sc & sh) | (sd & ss));
                        retval = ulongToUInt(HANDTYPE_VALUE_TRIPS + (lktable.topCardTable[getUnsignedInt(three_mask)] << TOP_CARD_SHIFT));
                        t = ranks ^ three_mask; /* Only one bit set in three_mask */
                        second = lktable.topCardTable[getUnsignedInt(t)];
                        retval += (second << SECOND_CARD_SHIFT);
                        t ^= (1L << (int)second);
                        retval += ulongToUInt(lktable.topCardTable[getUnsignedInt(t)] << THIRD_CARD_SHIFT);
                        return retval;
                    }

                default:
                    /* Possible quads, fullhouse, straight or flush, or two pair */
                    four_mask = sh & sd & sc & ss;
                    if (four_mask != 0)
                    {
                        long tc = lktable.topCardTable[getUnsignedInt(four_mask)];
                        retval = ulongToUInt(HANDTYPE_VALUE_FOUR_OF_A_KIND
                            + (tc << TOP_CARD_SHIFT)
                            + ((lktable.topCardTable[getUnsignedInt(ranks ^ (1L << (int)tc))]) << SECOND_CARD_SHIFT));
                        return retval;
                    };

                    /* Technically, three_mask as defined below is really the set of
                       bits which are set in three or four of the suits, but since
                       we've already eliminated quads, this is OK */
                    /* Similarly, two_mask is really two_or_four_mask, but since we've
                       already eliminated quads, we can use this shortcut */

                    two_mask = ranks ^ (sc ^ sd ^ sh ^ ss);
                    if (lktable.nBitsTable[getUnsignedInt(two_mask)] != n_dups)
                    {
                        /* Must be some trips then, which really means there is a 
                           full house since n_dups >= 3 */
                        long tc, t;
                        three_mask = ((sc & sd) | (sh & ss)) & ((sc & sh) | (sd & ss));
                        retval = HANDTYPE_VALUE_FULLHOUSE;
                        tc = lktable.topCardTable[getUnsignedInt(three_mask)];
                        retval += (tc << TOP_CARD_SHIFT);
                        t = (two_mask | three_mask) ^ (1L << (int)tc);
                        retval += ulongToUInt(lktable.topCardTable[getUnsignedInt(t)] << SECOND_CARD_SHIFT);
                        return retval;
                    };

                    if (retval != 0) /* flush and straight */
                        return retval;
                    else
                    {
                        /* Must be two pair */
                        long top, second;

                        retval = HANDTYPE_VALUE_TWOPAIR;
                        top = lktable.topCardTable[getUnsignedInt(two_mask)];
                        retval += (top << TOP_CARD_SHIFT);
                        second = lktable.topCardTable[getUnsignedInt(two_mask ^ (1 << (int)top))];
                        retval += (second << SECOND_CARD_SHIFT);
                        retval += ulongToUInt((lktable.topCardTable[getUnsignedInt(ranks ^ (1L << (int)top) ^ (1 << (int)second))]) << THIRD_CARD_SHIFT);
                        return retval;
                    }
            }
        }
        
        
        /*** lookup table ******/
        // Lookup Tables

        // Fast BitCounter
        // Bit count table from snippets.org
        private  final byte[] bits =
		{
			0, 1, 1, 2, 1, 2, 2, 3, 1, 2, 2, 3, 2, 3, 3, 4,  /* 0   - 15  */
			1, 2, 2, 3, 2, 3, 3, 4, 2, 3, 3, 4, 3, 4, 4, 5,  /* 16  - 31  */
			1, 2, 2, 3, 2, 3, 3, 4, 2, 3, 3, 4, 3, 4, 4, 5,  /* 32  - 47  */
			2, 3, 3, 4, 3, 4, 4, 5, 3, 4, 4, 5, 4, 5, 5, 6,  /* 48  - 63  */
			1, 2, 2, 3, 2, 3, 3, 4, 2, 3, 3, 4, 3, 4, 4, 5,  /* 64  - 79  */
			2, 3, 3, 4, 3, 4, 4, 5, 3, 4, 4, 5, 4, 5, 5, 6,  /* 80  - 95  */
			2, 3, 3, 4, 3, 4, 4, 5, 3, 4, 4, 5, 4, 5, 5, 6,  /* 96  - 111 */
			3, 4, 4, 5, 4, 5, 5, 6, 4, 5, 5, 6, 5, 6, 6, 7,  /* 112 - 127 */
			1, 2, 2, 3, 2, 3, 3, 4, 2, 3, 3, 4, 3, 4, 4, 5,  /* 128 - 143 */
			2, 3, 3, 4, 3, 4, 4, 5, 3, 4, 4, 5, 4, 5, 5, 6,  /* 144 - 159 */
			2, 3, 3, 4, 3, 4, 4, 5, 3, 4, 4, 5, 4, 5, 5, 6,  /* 160 - 175 */
			3, 4, 4, 5, 4, 5, 5, 6, 4, 5, 5, 6, 5, 6, 6, 7,  /* 176 - 191 */
			2, 3, 3, 4, 3, 4, 4, 5, 3, 4, 4, 5, 4, 5, 5, 6,  /* 192 - 207 */
			3, 4, 4, 5, 4, 5, 5, 6, 4, 5, 5, 6, 5, 6, 6, 7,  /* 208 - 223 */
			3, 4, 4, 5, 4, 5, 5, 6, 4, 5, 5, 6, 5, 6, 6, 7,  /* 224 - 239 */
			4, 5, 5, 6, 5, 6, 6, 7, 5, 6, 6, 7, 6, 7, 7, 8   /* 240 - 255 */
		};


       
        /**
         * Fast Bitcounting method (adapted from snippets.org)
         * @param bitField long to count
         * @return number of set bits in long
         */
        public  int BitCount(long bitField)
        {
            return
                bits[(int)(bitField & 0x00000000000000FFL)] +
                bits[(int)((bitField & 0x000000000000FF00L) >>> 8)] +
                bits[(int)((bitField & 0x0000000000FF0000L) >>> 16)] +
                bits[(int)((bitField & 0x00000000FF000000L) >>> 24)] +
                bits[(int)((bitField & 0x000000FF00000000L) >>> 32)] +
                bits[(int)((bitField & 0x0000FF0000000000L) >>> 40)] +
                bits[(int)((bitField & 0x00FF000000000000L) >>> 48)] +
                bits[(int)((bitField & 0xFF00000000000000L) >>> 56)];
        }
        
        
        /********************* OpponentModel reweight ***********/
        
        /**
         * 
         * @param u 平均牌力
         * @param v 误差
         * @param weight 要更新的权重表
         * @param round 当前是第几轮
         */
        public void postflopReweight(final float u, final float v, 
        		final HashMap<Long, Float> weight, final State round) {
        	
        	final float low_wt = 0.01f, high_wt = 1.0f;
        	float reweight = 0;
        	// 遍历产生对手可能的手牌
        	 // Rank our hand
        	long pocket = myPocketCardMask,  board = boardCardMask;
            long arg1 = pocket | board;
            int arg2 = BitCount(pocket | board);
            long ourrank = Evaluate(arg1, arg2);
            // Mark known cards as dead.
            long dead_cards = pocket | board;
            
            // Iterate through all possible opponent pocket cards
            List<Long> res = Hands(0L, dead_cards, 2);
            for(int i = 0; i < res.size(); i++ ) {
            	long oppPocket = res.get(i);
                float ehs = calOppHandStrength(oppPocket);
                reweight = (ehs - u + v) / (2*v);
                if(reweight < low_wt) reweight = low_wt;
                if(reweight > high_wt) reweight = high_wt;
                float weightValue = 1;
                if(weight.containsKey(oppPocket)){
                	weightValue = weight.get(oppPocket);
                	weightValue = weightValue * reweight;
                }else{
                	// 如果没有，使用值=1
                	weightValue = 1;
                	weightValue = weightValue * reweight;
                }
                if(weightValue < low_wt){
                	weightValue = low_wt;
                }
                weight.put(oppPocket, weightValue);
                
            }
        }
        
        /**
         * 特别用来计算对手的手牌强度
         * @param pocket
         * @param board
         * @return
         */
        private float calOppHandStrength(final long pocket) {
        	float hs = 0;
        	int ahead = 0;
            int tied = 0;
            int behind = 0;
        	 long board = boardCardMask;
        	 long arg1 = pocket | board;
             int arg2 = BitCount(pocket | board);
             long ourrank = Evaluate(arg1, arg2);
             // Mark known cards as dead.
             long dead_cards = pocket | board;
             // TODO 还要除去自己手上的牌？
             dead_cards |= myPocketCardMask;
             // 已经有两张手牌，接下来应该是计算所有对手的手牌，然后
             List<Long> res = Hands(0L, dead_cards, 2);
             for(long oppPocket : res) {
                 // Note Current State
                 long opprank = Evaluate(oppPocket | board, BitCount(oppPocket | board));
                 if(opprank > ourrank) behind++;
                 else if(opprank < ourrank) ahead++;
                 else if(opprank == ourrank) tied++;
             }
             // 计算手牌强度
             hs = (ahead + 0.5f * tied)  / (tied + ahead + behind);
             return hs;
        }
        
        /**
         * 
         * @param pocket
         * @param shared 表示已经知道的牌，比如在计算对手的牌力时，我的手牌算是知道的
         * @return
         */
        private float fastCalOppHandStrength(final long pocket, final long shared) {
	        	 int ahead = 2;
	             int tied = 1;
	             int behind = 0;
	             int index = behind;
	             int[] HPTotal = new int[3];
	             for(int i = 0; i < 3; i++){
	            	 HPTotal[i] = 0;
	             }
	        	int _i1;
	        	long _card1;
	        	long board = boardCardMask;
	        	long arg1 = pocket | board;
	            int arg2 = BitCount(pocket | board);
	            long ourrank = Evaluate(arg1, arg2);
	            // Mark known cards as dead.
	            long dead = pocket | board;
	            dead |= shared;
        	 for (_i1 = 0; _i1 < lktable.TwoCardTable.length; _i1++)
	          {
	              _card1 = lktable.TwoCardTable[_i1];
	              if ((dead & _card1) != 0) continue;	
	              long oppPocket = _card1 | shared;
	              long opprank = Evaluate(oppPocket | board, BitCount(oppPocket | board));
	              if (ourrank > opprank)
	              {
	            	  // 枚举完对手底牌，到公牌
	                  //HandPotentialOpp(pocket, board, oppPocket, ahead,  HP);
	            	  index = ahead;
	                  HPTotal[ahead]++;
	              }
	              else if (ourrank == opprank)
	              {
	                  //HandPotentialOpp(pocket, board, oppPocket, tied,  HP);
	            	  index = tied;
	                  HPTotal[tied]++;
	              }
	              else
	              {
	                  //HandPotentialOpp(pocket, board, oppPocket, behind,  HP);
	            	  index = behind;
	                  HPTotal[behind]++;
	              }
	          }
        	 float strength = (HPTotal[ahead] + 0.5f * HPTotal[tied])  / 
     				(HPTotal[tied] + HPTotal[ahead] + HPTotal[behind]);
        	 return strength;
        }
        
        public void fastPostflopReweight(final float u, final float v, 
        		final float[] weight, final State round) {
        	final float low_wt = 0.01f, high_wt = 1.0f;
        	float reweight = 0;
        	// 遍历产生对手可能的手牌
        	 // Rank our hand
        	long pocket = myPocketCardMask,  board = boardCardMask;
            // Mark known cards as dead.
            long dead_cards = pocket | board;
            long _card1, _card2;           
            long shared = 0L;
            int _i1, _i2;
             // 枚举对手两张牌
	         for (_i1 = 0; _i1 < lktable.TwoCardTable.length; _i1++)
	         {
	             _card1 = lktable.TwoCardTable[_i1];
	             if ((dead_cards & _card1) != 0) continue;	
	             // _i1就表示了这两张手牌的索引值！所以opponents里面也可以用数组来表示weight!
	             long oppPocket = _card1 | shared;
	             // 出来手牌之后，还得计算对手手牌的牌力！
	             float ehs = fastCalOppHandStrength(oppPocket, pocket);
	             reweight = (ehs - u + v) / (2*v);
	                if(reweight < low_wt) reweight = low_wt;
	                if(reweight > high_wt) reweight = high_wt;
	                float weightValue = 1;
	                weightValue = weight[_i1];
	                weightValue = weightValue * reweight;
//	                if(weight.containsKey(oppPocket)){
//	                	weightValue = weight.get(oppPocket);
//	                	weightValue = weightValue * reweight;
//	                }else{
//	                	// 如果没有，使用值=1
//	                	weightValue = 1;
//	                	weightValue = weightValue * reweight;
//	                }
	                if(weightValue < low_wt){
	                	weightValue = low_wt;
	                }
	                weight[_i1] = weightValue;
	         }	         	                 
        }

        /**
         * 速度更快
         * @param pocket
         * @param board
         * @return
         */
        private double[] fastHandPotential(long pocket, long board)
        {
            int ahead = 2;
            int tied = 1;
            int behind = 0;
            int index = behind;
            double ppot = 0, npot = 0;
            int[][] HP = new int[3][3];
            int[] HPTotal = new int[3];
            int cards = BitCount(pocket | board);
            double mult = (cards == 5 ? 990.0 : 45.0);

            // Initialize
            for (int i = 0; i < 3; i++)
            {
                HPTotal[i] = 0;
                for (int j = 0; j < 3; j++)
                {
                    HP[i][j] = 0;
                }
            }

            // Rank our hand
            long arg1 = pocket | board;
            int arg2 = BitCount(pocket | board);
            long ourrank = Evaluate(arg1, arg2);
            // Mark known cards as dead.
            long dead_cards = pocket | board;
            
            // Iterate through all possible opponent pocket cards
            // TODO 把Hands函数提取出来
            int _i1, _i2, length;
            long _card1, _card2;           
            long shared = 0L, dead = dead_cards; 
            dead |= shared;
            // 枚举对手两张牌
	         for (_i1 = 0; _i1 < lktable.TwoCardTable.length; _i1++)
	          {
	              _card1 = lktable.TwoCardTable[_i1];
	              if ((dead & _card1) != 0) continue;	
	              long oppPocket = _card1 | shared;
	              long opprank = Evaluate(oppPocket | board, BitCount(oppPocket | board));
	              if (ourrank > opprank)
	              {
	            	  // 枚举完对手底牌，到公牌
	                  //HandPotentialOpp(pocket, board, oppPocket, ahead,  HP);
	            	  index = ahead;
	                  HPTotal[ahead]++;
	              }
	              else if (ourrank == opprank)
	              {
	                  //HandPotentialOpp(pocket, board, oppPocket, tied,  HP);
	            	  index = tied;
	                  HPTotal[tied]++;
	              }
	              else
	              {
	                  //HandPotentialOpp(pocket, board, oppPocket, behind,  HP);
	            	  index = behind;
	                  HPTotal[behind]++;
	              }
	              // 现在该枚举公牌了
	              long inner_dead_cards = pocket | board | oppPocket;
	              long ourbest, oppbest;
	              int numberOfCards = 7-BitCount(pocket | board);
	              // 最多能枚举两张而已了
	              int innerlength = 0;
	              switch (numberOfCards)
	              {
	              case 2:
	                    for (_i2 = 0; _i2 < lktable.TwoCardTable.length; _i2++)
	                    {
	                        _card2 = lktable.TwoCardTable[_i2];
	                        if ((inner_dead_cards & _card2) != 0) continue;
	                        long handmask = _card2 | shared;
	                        ourbest = Evaluate(pocket | board | handmask, 7);
	  	                  	oppbest = Evaluate(oppPocket | board | handmask, 7);
		  	                if (ourbest > oppbest)
		  	                      HP[index][ahead]++;
		  	                 else if (ourbest == oppbest)
		  	                      HP[index][tied]++;
		  	                 else
		  	                      HP[index][behind]++;
	                    }
	                    break;
	                case 1:
	                    for (_i2 = 0; _i2 < lktable.CardMasksTable.length; _i2++)
	                    {
	                        _card2 = lktable.CardMasksTable[_i2];
	                        if ((inner_dead_cards & _card2) != 0) continue;
	                        long handmask =  _card2 | shared;
	                        ourbest = Evaluate(pocket | board | handmask, 7);
	  	                  	oppbest = Evaluate(oppPocket | board | handmask, 7);
		  	                if (ourbest > oppbest)
		  	                      HP[index][ahead]++;
		  	                 else if (ourbest == oppbest)
		  	                      HP[index][tied]++;
		  	                 else
		  	                      HP[index][behind]++;
	                    }
	                    break;
	                case 0:
	                	 long handmask =  shared;
	                	 ourbest = Evaluate(pocket | board | handmask, 7);
	  	                 oppbest = Evaluate(oppPocket | board | handmask, 7);
		  	             if (ourbest > oppbest)
		  	                HP[index][ahead]++;
		  	            else if (ourbest == oppbest)
		  	                 HP[index][tied]++;
		  	            else
		  	                 HP[index][behind]++;
	                    break;
	              }	  
	           }   
            double den1 = (mult * (HPTotal[behind] + (HPTotal[tied] / 2.0)));
            double den2 = (mult * (HPTotal[ahead] + (HPTotal[tied] / 2.0)));
            if (den1 > 0)
                ppot = (HP[behind][ahead] + (HP[behind][tied] / 2) + (HP[tied][ahead] / 2)) / (double)den1;
            else
                ppot = 0;
            if (den2 > 0)
                npot = (HP[ahead][behind] + (HP[ahead][tied] / 2) + (HP[tied][behind] / 2)) / (double)den2;
            else
                npot = 0;
            double strength = (HPTotal[ahead] + 0.5f * HPTotal[tied])  / 
    				(HPTotal[tied] + HPTotal[ahead] + HPTotal[behind]);
    		double result[] = new double[2];
//    		 Logger.Log("in new:HPTotal[ahead]="+HPTotal[ahead]+" HPTotal[tied]="+HPTotal[tied]+" HPTotal[behind]="+HPTotal[behind]);
//     		Logger.Log("in new:strength="+strength+" ppot="+ppot);
    		result[0] = ppot;
    		result[1] = strength;
    		return result;
        }
}

