package FTP;

    public abstract class Host {

        final static int WINDOW_SIZE = 5;
        final static int PAYLOAD_SIZE = 1000;
        final static int BUFFER_SIZE = 1005;
        private final static int MAX_SEQ_NUM = 15;

        public int incrementSeqNumber(int number) {
            return (++number)%MAX_SEQ_NUM;
        }

        public boolean isSeqNumInWindow(int baseSeq, int seqNum) {

            if(baseSeq + WINDOW_SIZE < MAX_SEQ_NUM) {
                if(seqNum >= baseSeq && seqNum < baseSeq + WINDOW_SIZE) {
                    return true;
                }
            }
            else {
                if(seqNum >= baseSeq && seqNum < MAX_SEQ_NUM ||
                        seqNum >= 0 && seqNum < (baseSeq+WINDOW_SIZE)%MAX_SEQ_NUM ) {
                    return true;
                }
            }
            return false;
        }

        public boolean isSeqNumInCurrOrPrevWindow(int baseSeq, int seqNum) {

            int oldBase = (baseSeq - WINDOW_SIZE)%MAX_SEQ_NUM;
            if(oldBase < 0) oldBase += MAX_SEQ_NUM;
            return isSeqNumInWindow(oldBase, seqNum) ||
                    isSeqNumInWindow(baseSeq, seqNum);
        }

    }
