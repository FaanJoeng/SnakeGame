package edu.li.snake;

import javax.swing.*;
import java.applet.Applet;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.*;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Timer;

/**
 * 贪吃蛇游戏
 * W/S/A/D及方向键控制蛇移动方向
 *
 * @author Yang Fan
 */
public class SnakeGame implements Serializable {

    private static final long serialVersionUID = 3126603903820639651L;

    // 常量定义 FRAME为1000px*750px 蛇活动区域为750px*750px 图标均为25px*25px
    private static final int FRAME_WIDTH = 1000;
    private static final int FRAME_HEIGHT = 750;
    private static final int GARDEN_WIDTH = 750;
    private static final int GARDEN_HEIGHT = 750;
    private static final int ICON_SIZE = 25;

    // 难度等级
    private static final int DIFFICULTY_EASY = 500; // 容易
    private static final int DIFFICULTY_NORMAL = 300; // 正常
    private static final int DIFFICULT_HARD = 100; // 困难
    private static final int DIFFICULT_HELL = 50; // 地狱

    // 记录蛇身体位置
    private volatile LinkedList<Position> snake;

    // 水果位置
    private Position fruit;

    // 在调整方向前移动方向
    private String previousDirection = DirectionEnum.RIGHT.getDirection();

    // 当前移动方向
    private String currentDirection = DirectionEnum.RIGHT.getDirection();

    // 水果是否被吃了
    private boolean isFruitExist = true;

    // 难度 70-地狱模式 100-困难 300-普通 500-容易
    private int difficulty = DIFFICULTY_NORMAL;

    // 当前游戏难度字符串表示
    private String difficultyString;

    // 分数 每吃一颗水果加一分
    private int score = 0;

    // 历史记录
    private ArrayList<History> histories;

    // 移动方向枚举
    enum DirectionEnum {
        UP("U"), // 上
        DOWN("D"), // 下
        LEFT("L"), // 左
        RIGHT("R"); // 右

        String direction;

        DirectionEnum(String direction) {
            this.direction = direction;
        }

        public String getDirection() {
            return direction;
        }
    }

    // 图标枚举
    enum IconEnum {
        ICON_UP(new ImageIcon(new Object().getClass().getResource("/icon/up.jpg"))),
        ICON_DOWN(new ImageIcon(new Object().getClass().getResource("/icon/down.jpg"))),
        ICON_LEFT(new ImageIcon(new Object().getClass().getResource("/icon/left.jpg"))),
        ICON_RIGHT(new ImageIcon(new Object().getClass().getResource("/icon/right.jpg"))),
        ICON_FRUIT(new ImageIcon(new Object().getClass().getResource("/icon/fruit.jpg"))),
        ICON_SNAKE_BODY(new ImageIcon(new Object().getClass().getResource("/icon/snake_body.jpg"))),
        ICON_BACKGROUND(new ImageIcon(new Object().getClass().getResource("/image/background.jpg")));

        private ImageIcon icon;

        IconEnum(ImageIcon icon) {
            this.icon = icon;
        }

        public Image getIcon() {
            return icon.getImage();
        }
    }

    private SnakeGame() {
        prepareUI();
        playAudio();
    }

    public static void main(String[] args) {
        new SnakeGame();
    }

    /**
     * 界面渲染
     */
    private void prepareUI() {
        JFrame gameFrame = new JFrame("SnakeGame");
        GardenPanel gardenPanel = new GardenPanel();
        ScorePanel scorePanel = new ScorePanel();

        gameFrame.add(gardenPanel);
        gameFrame.add(scorePanel);

        gameFrame.addKeyListener(new KeyPressedHandler());

        gameFrame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        gameFrame.setLayout(null);
        gameFrame.setResizable(false);
        gameFrame.setSize(FRAME_WIDTH, FRAME_HEIGHT);
        gameFrame.setVisible(true);
        gameFrame.setLocationRelativeTo(null);
    }

    // 创建新的线程播放音乐
    private void playAudio() {
        new Thread(() -> {
            try {
                File audioFile = new File(getClass().getResource("/audio/background.wav").toURI());
                Applet.newAudioClip(audioFile.toURI().toURL()).loop();
            } catch (URISyntaxException | MalformedURLException e) {
                System.err.println("音频播放失败");
            }
        }).start();
    }

    /**
     * 获取histories排序后的字符串
     *
     * @return 拼接好的html字符串
     */
    private String getHistoriesString() {

        ArrayList<History> histories = this.histories;
        StringBuilder sb = new StringBuilder();

        sb.append("<tr><td>Rank</td><td>Score</td><td>Mode</td><td>Date</td><br>");

        if (histories.size() > 0) {
            // 历史记录以分数排序
            Collections.sort(histories);

            for (int i = 0; i < histories.size() && i < 20; i++) {
                History history = histories.get(i);
                sb.append("<tr><td align='center'>")
                        .append(i + 1).append("</td><td align='center'>")
                        .append(history.getScore())
                        .append("</td><td align='center'>")
                        .append(history.getMode())
                        .append("</td><td align='center'>")
                        .append(history.getDate())
                        .append("</td></tr><br>");
            }
        }

        sb.append("</body></html>");

        return sb.toString();
    }

    private class GardenPanel extends JPanel {
        private static final long serialVersionUID = 30724355310306213L;

        private JPanel gardenPanel = this;

        private GardenPanel() {
            snake = new LinkedList<>();

            // 随机生成头节点
            int left = new Random().nextInt(GARDEN_WIDTH - ICON_SIZE * 5) % ICON_SIZE * ICON_SIZE;
            int top = new Random().nextInt(GARDEN_HEIGHT - ICON_SIZE * 5) % ICON_SIZE * ICON_SIZE;
            snake.add(new Position(left, top));


            setBounds(0, 0, GARDEN_WIDTH, GARDEN_HEIGHT);


            // 随机生成开始方向
            previousDirection = currentDirection = DirectionEnum.values()[new Random().nextInt(3)].getDirection();

            fruit = generateFruit();

            // 弹出难度选择对话框
            chooseDifficulty(this);

            // UI重绘定时器 每50ms刷新一下页面
            Timer repaintTimer = new Timer();

            // 蛇移动定时器
            Timer directionTimer = new Timer();

            repaintTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    repaint();
                }
            }, 50, 50);

            directionTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    try {
                        while (true) {
                            if (isFruitExist) {
                                fruit = generateFruit();
                            }
                            switch (currentDirection) {
                                case "U":
                                    isFruitExist = moveUp(fruit);
                                    break;
                                case "D":
                                    isFruitExist = moveDown(fruit);
                                    break;
                                case "L":
                                    isFruitExist = moveLeft(fruit);
                                    break;
                                case "R":
                                    isFruitExist = moveRight(fruit);
                                    break;
                            }
                            Thread.sleep(difficulty);
                        }
                    } catch (Exception e) {
                        saveScoreToFile();

                        repaintTimer.cancel();
                        directionTimer.cancel();

                        JOptionPane.showMessageDialog(gardenPanel, "游戏结束", "游戏结束", JOptionPane.WARNING_MESSAGE);
                    }

                }
            }, 50, 100);
        }

        /**
         * 保存分数到文件
         */
        private void saveScoreToFile() {
            try {
                if (histories == null) {
                    histories = new ArrayList<>();
                }
                histories.add(new History(score, difficultyString, new Date()));
                FileOutputStream fos = new FileOutputStream(new File(getClass().getResource("/history/history").toURI()));
                ObjectOutputStream oos = new ObjectOutputStream(fos);
                oos.writeObject(histories);
                oos.close();
                fos.close();
            } catch (URISyntaxException | IOException e) {
                e.printStackTrace();
                System.err.println("存储日志失败");
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            g.drawImage(IconEnum.ICON_BACKGROUND.getIcon(), 0, 0, GARDEN_WIDTH, GARDEN_HEIGHT, null);
            g.drawImage(IconEnum.ICON_FRUIT.getIcon(), fruit.getLeft(), fruit.getTop(), ICON_SIZE, ICON_SIZE, null);

            LinkedList<Position> snakeBody = snake;

            Image snakeHeadIcon = null;

            switch (previousDirection) {
                case "U":
                    snakeHeadIcon = IconEnum.ICON_UP.getIcon();
                    break;
                case "D":
                    snakeHeadIcon = IconEnum.ICON_DOWN.getIcon();
                    break;
                case "L":
                    snakeHeadIcon = IconEnum.ICON_LEFT.getIcon();
                    break;
                case "R":
                    snakeHeadIcon = IconEnum.ICON_RIGHT.getIcon();
                    break;
            }

            for (int i = 0; i < snakeBody.size(); i++) {
                if (i == 0) {
                    // 绘制头
                    g.drawImage(snakeHeadIcon, snakeBody.get(i).getLeft(), snakeBody.get(i).getTop(), null);
                } else {
                    // 绘制身体
                    g.drawImage(IconEnum.ICON_SNAKE_BODY.getIcon(), snakeBody.get(i).getLeft(), snakeBody.get(i).getTop(), null);
                }
            }
        }
    }

    /**
     * 分数及历史记录面板
     */
    private class ScorePanel extends JPanel {
        private static final long serialVersionUID = -2948230467476062865L;

        private ScorePanel() {
            getHistoryFromFile();

            JLabel scoreLabel = new JLabel();
            scoreLabel.setText("<html><body style='font-size:20px'>Record:" + score + "<br></body></html>");
            scoreLabel.setHorizontalAlignment(JLabel.LEFT);
            scoreLabel.setFont(new Font(null, Font.PLAIN, 18));


            JLabel historyLabel = new JLabel();
            historyLabel.setText("<html><body><br>" + getHistoriesString());
            historyLabel.setHorizontalAlignment(JLabel.LEFT);
            historyLabel.setFont(new Font(null, Font.PLAIN, 14));

            add(scoreLabel);
            add(historyLabel);

            setBounds(GARDEN_WIDTH, 0, FRAME_WIDTH - GARDEN_WIDTH, GARDEN_HEIGHT);

            // UI重绘定时器 每50ms刷新一下页面
            Timer repaintTimer = new Timer();

            repaintTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    scoreLabel.setText("Score:" + score);
                    repaint();
                }
            }, 50, 50);
        }

        /**
         * 从文件加载历史记录
         */
        private void getHistoryFromFile() {
            try {
                FileInputStream fis = new FileInputStream(new File(getClass().getResource("/history/history").toURI()));
                ObjectInputStream ois = new ObjectInputStream(fis);
                histories = (ArrayList<History>) ois.readObject();
                fis.close();
                ois.close();
            } catch (URISyntaxException | IOException | ClassNotFoundException e) {
                histories = new ArrayList<>();
            }
        }

        @Override
        public void paint(Graphics g) {
            super.paint(g);
        }
    }

    private void chooseDifficulty(JPanel panel) {
        Object[] options = {"Easy", "Normal", "Hard", "Hell"};

        int response = JOptionPane.showOptionDialog(panel, "请选择游戏难度：", "游戏难度选择", JOptionPane.YES_NO_CANCEL_OPTION,
                JOptionPane.QUESTION_MESSAGE, null, options, options[0]);

        difficultyString = (String) options[response];

        switch (response) {
            case 0:
                difficulty = DIFFICULTY_EASY;
                break;
            case 1:
                difficulty = DIFFICULTY_NORMAL;
                break;
            case 2:
                difficulty = DIFFICULT_HARD;
                break;
            case 3:
                difficulty = DIFFICULT_HELL;
                break;
            default:
                difficulty = DIFFICULTY_NORMAL;
        }
    }

    /**
     * 生成水果
     */
    private Position generateFruit() {
        while (true) {
            int top = new Random().nextInt(GARDEN_HEIGHT - ICON_SIZE) % ICON_SIZE * ICON_SIZE;
            int left = new Random().nextInt(GARDEN_WIDTH - ICON_SIZE) % ICON_SIZE * ICON_SIZE;
            Position newFruit = new Position(left, top);

            if (!snake.contains(newFruit)) {
                return newFruit;
            }
        }
    }

    //--蛇相关操作--//
    private boolean moveUp(Position fruit) {
        Position oldHead = snake.getFirst();
        Position newHead = new Position(oldHead.getLeft(), oldHead.getTop() - ICON_SIZE);

        checkConflict(newHead);

        snake.addFirst(newHead);

        if (!fruit.equals(newHead)) {
            snake.removeLast();
        } else {
            ++score;
            return true;
        }

        return false;
    }

    private boolean moveDown(Position fruit) {
        Position oldHead = snake.getFirst();
        Position newHead = new Position(oldHead.getLeft(), oldHead.getTop() + ICON_SIZE);

        checkConflict(newHead);
        snake.addFirst(newHead);

        if (!fruit.equals(newHead)) {
            snake.removeLast();
        } else {
            ++score;
            return true;
        }

        return false;
    }

    private boolean moveLeft(Position fruit) {
        Position oldHead = snake.getFirst();
        Position newHead = new Position(oldHead.getLeft() - ICON_SIZE, oldHead.getTop());

        checkConflict(newHead);
        snake.addFirst(newHead);

        if (!fruit.equals(newHead)) {
            snake.removeLast();
        } else {
            ++score;
            return true;
        }

        return false;
    }

    private boolean moveRight(Position fruit) {
        Position oldHead = snake.getFirst();
        Position newHead = new Position(oldHead.getLeft() + ICON_SIZE, oldHead.getTop());

        checkConflict(newHead);
        snake.addFirst(newHead);

        if (!fruit.equals(newHead)) {
            snake.removeLast();
        } else {
            ++score;
            return true;
        }

        return false;
    }

    /**
     * 冲突检测
     * 如果(1)新生成头节点在garden外 (2)或者在蛇身体内即冲突  抛出异常，游戏结束
     *
     * @param newHead 新生成头节点位置
     * @throws Exception 碰撞
     */
    private void checkConflict(Position newHead) {
        if (snake.contains(newHead) || newHead.getLeft() < 0 || newHead.getLeft() + ICON_SIZE > GARDEN_WIDTH
                || newHead.getTop() < 0 || newHead.getTop() + ICON_SIZE > GARDEN_HEIGHT) {
            throw new RuntimeException("冲突了");
        }
    }

    /**
     * 上下左右按键处理器
     */
    class KeyPressedHandler extends KeyAdapter {
        @Override
        public void keyPressed(KeyEvent e) {
            super.keyPressed(e);

            int keyCode = e.getKeyCode();

            if (keyCode == KeyEvent.VK_UP || keyCode == KeyEvent.VK_W) {
                changeDirection(DirectionEnum.DOWN.getDirection(), DirectionEnum.UP.getDirection());
            }

            if (keyCode == KeyEvent.VK_DOWN || keyCode == KeyEvent.VK_S) {
                changeDirection(DirectionEnum.UP.getDirection(), DirectionEnum.DOWN.getDirection());
            }

            if (keyCode == KeyEvent.VK_LEFT || keyCode == KeyEvent.VK_A) {
                changeDirection(DirectionEnum.RIGHT.getDirection(), DirectionEnum.LEFT.getDirection());
            }

            if (keyCode == KeyEvent.VK_RIGHT || keyCode == KeyEvent.VK_D) {
                changeDirection(DirectionEnum.LEFT.getDirection(), DirectionEnum.RIGHT.getDirection());
            }
        }

        /**
         * 更改方向状态
         * 如果要调整的方向与当前方向相同或相反，不做操作
         *
         * @param previousDirectionEnum
         * @param currentDirectionEnum
         */
        private void changeDirection(String previousDirectionEnum, String currentDirectionEnum) {
            if (previousDirection.equals(previousDirectionEnum)) {
                return;
            }

            currentDirection = currentDirectionEnum;
            if (currentDirection.equals(previousDirection)) {
                return;
            }
            previousDirection = currentDirection;
        }
    }

    /**
     * 记录蛇身体位置 以garden左上角为原点
     */
    private class Position implements Serializable {
        private static final long serialVersionUID = 8872072725153475740L;

        private int left;
        private int top;

        private Position(int left, int top) {
            this.left = left;
            this.top = top;
        }

        private int getLeft() {
            return left;
        }

        private int getTop() {
            return top;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Position position = (Position) o;

            if (left != position.left) return false;
            return top == position.top;
        }

        @Override
        public int hashCode() {
            int result = left;
            result = 31 * result + top;
            return result;
        }
    }

    // 历史记录对象
    class History implements Serializable, Comparable {

        private static final long serialVersionUID = 4351767007118837092L;

        private int score;
        private String mode;
        private Date date;

        private History(int score, String mode, Date date) {
            this.score = score;
            this.mode = mode;
            this.date = date;
        }

        private int getScore() {
            return score;
        }

        private String getMode() {
            return mode;
        }

        private String getDate() {
            SimpleDateFormat sdf = new SimpleDateFormat(" yyyy-MM-dd");
            return sdf.format(date);
        }

        @Override
        public int compareTo(Object o) {
            History history = (History) o;
            if (score > history.getScore()) {
                return -1;
            }

            return 1;
        }
    }
}
