import java.util.List;
import java.util.ArrayList;

public class IntervalTree {
    private volatile TreeNode root;

    public IntervalTree() {
        root = null;
    }

    public void insert(int start, int end, boolean acceptingInterval) {
        TreeNode newNode = new TreeNode(new Interval(start, end, acceptingInterval), end);
        if (root == null) {
            root = newNode;
        } else {
            root.insert(newNode);
        }
    }

    public boolean isAddressAllowed(int address) {
        if (root != null) {
            List<Interval> results = new ArrayList<>();
            root.findIntervalsContainingValue(address, results);
            // find the latest interval and return its boolean
            Interval latestInterval = null;
            for (Interval interval : results) {
                if (latestInterval == null) {
                    latestInterval = interval;
                } else if (latestInterval.getCreatedAt() < interval.getCreatedAt()) {
                    latestInterval = interval;
                }
            }

            return latestInterval == null || latestInterval.isAcceptingInterval();
        }
        return true;
    }

    private class Interval {
        private final int start;
        private final int end;
        private final boolean acceptingInterval;
        private final long createdAt;

        private Interval(int start, int end, boolean acceptingInterval) {
            this.start = start;
            this.end = end;
            this.acceptingInterval = acceptingInterval;
            this.createdAt = System.currentTimeMillis();
        }

        public int getEnd() {
            return end;
        }

        public int getStart() {
            return start;
        }

        public boolean isAcceptingInterval() {
            return acceptingInterval;
        }

        public long getCreatedAt() {
            return createdAt;
        }

        public boolean containsValue(int value) {
            return value >= start && value <= end;
        }
    }

    private class TreeNode {
        private volatile Interval interval;
        private volatile int max;
        private volatile TreeNode left = null;
        private volatile TreeNode right = null;
        private volatile int height = 0;

        private TreeNode(Interval interval, int max) {
            this.interval = interval;
            this.max = max;
        }

        private int getStart() {
            return interval.getStart();
        }

        private int getEnd() {
            return interval.getEnd();
        }

        private void attemptUpdateMax(int value) {
            max = Math.max(max, value);
        }

        private void setHeight(int height) {
            this.height = height;
        }

        private int height(TreeNode node) {
            return node == null ? -1 : node.height;
        }

        private TreeNode rotateWithLeftChild(TreeNode node) {
            TreeNode leftNode = node.left;
            node.left = leftNode.right;
            leftNode.right = node;
            // fix height
            node.height = Math.max(height(node.left), height(node.right)) + 1;
            leftNode.height = Math.max(height(leftNode.left), node.height) + 1;

            // fix max
            int tmpLeftMax = leftNode.max;
            leftNode.max = Math.max(node.max, leftNode.getEnd());
            node.max = Math.max(tmpLeftMax, node.getEnd());
            return leftNode;
        }

        private TreeNode rotateWithRightChild(TreeNode node) {
            TreeNode rightNode = node.right;
            node.right = rightNode.left;
            rightNode.left = node;
            // fix height
            node.height = Math.max(height(node.left), height(node.right)) + 1;
            rightNode.height = Math.max(height(rightNode.right), node.height) + 1;

            // fix max
            int tmpRightMax = rightNode.max;
            rightNode.max = Math.max(node.max, rightNode.getEnd());
            node.max = Math.max(tmpRightMax, node.getEnd());
            return rightNode;
        }

        private TreeNode doubleWithRightChild(TreeNode node) {
            node.right = rotateWithLeftChild(node.right);
            return rotateWithRightChild(node);
        }
        private TreeNode doubleWithLeftChild(TreeNode node) {
            node.left = rotateWithRightChild(node.right);
            return rotateWithLeftChild(node);
        }

        private TreeNode balance(TreeNode node) {
            if (height(node.left) - height(node.right) > 1) {
                if (height(node.left.left) >= height(node.left.right)) {
                    node = rotateWithLeftChild(node);
                } else {
                    node = doubleWithLeftChild(node);
                }

            } else if (height(node.right) - height(node.left) > 1) {
                if (height(node.right.right) >= height(node.right.left)) {

                    node = rotateWithRightChild(node);
                } else {
                    node = doubleWithRightChild(node);
                }
            }

            node.height = Math.max(height(node.left), height(node.right)) + 1;
            return node;
        }

        private TreeNode insert(TreeNode node) {
            if (interval.getStart() > node.getStart()) {
                if (left == null) {
                    node.setHeight(height + 1);
                    left = node;
                } else {
                    left.insert(node);
                }
            } else {
                if (right == null) {
                    node.setHeight(height + 1);
                    right = node;
                } else {
                    right.insert(node);
                }
            }
            attemptUpdateMax(node.getEnd());
            return balance(this);
        }

        private void findIntervalsContainingValue(int value, List<Interval> results) {
            // if the point is greater than the max of this subtree, we're done
            if (max < value) {
                return;
            }

            // Search left children
            if (left != null) {
                left.findIntervalsContainingValue(value, results);
            }

            if (interval.containsValue(value)) {
                results.add(interval);
            }

            // If the point is less than the start of this node,
            // then it can't be in any child to the right.
            if (value < interval.getStart()) {
                return;
            }

            // Otherwise, search right children
            if (right != null) {
                right.findIntervalsContainingValue(value, results);
            }
        }

    }

//    // test with main
//    public static void main(String[] args) {
//        IntervalTree root = new IntervalTree();
//
//        // default allowed
//        assert(root.isAddressAllowed(200000));
//
//        root.insert(0, 20, true);
//        for (int i = 0; i < 21; i++) {
//            assert(root.isAddressAllowed(i));
//        }
//
//        root.insert(10, 30, false);
//        for (int i = 0; i < 10; i++) {
//            assert(root.isAddressAllowed(i));
//        }
//
//        for (int i = 10; i < 31; i++) {
//            assert(!root.isAddressAllowed(i));
//        }
//
//        root.insert(0, 30, true);
//        for (int i = 0; i < 31; i++) {
//            assert(root.isAddressAllowed(i));
//        }
//    }
}


