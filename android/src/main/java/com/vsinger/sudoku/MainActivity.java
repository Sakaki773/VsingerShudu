package com.vsinger.sudoku;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.pm.ActivityInfo;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

public class MainActivity extends Activity {
    private static final int CYAN = Color.rgb(102, 204, 255);
    private static final int CYAN_DARK = Color.rgb(22, 143, 201);
    private static final int CYAN_DEEP = Color.rgb(0, 108, 168);
    private static final int CYAN_SOFT = Color.rgb(221, 245, 255);
    private static final int GOLD = Color.rgb(255, 246, 143);
    private static final int GOLD_BUTTON = Color.rgb(255, 229, 130);
    private static final int DANGER = Color.rgb(230, 0, 45);
    private static final int DANGER_BG = Color.rgb(255, 226, 232);
    private static final int LINE = Color.rgb(184, 190, 194);
    private static final int NOTE = Color.rgb(49, 64, 71);
    private static final int MODE_RESULT = 0;
    private static final int MODE_ASSIST = 1;

    private final Random random = new Random();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<String, Integer> difficultyGivens = new HashMap<String, Integer>();

    private final int[] welcomeImages = {
            R.drawable.welcome_01,
            R.drawable.welcome_02,
            R.drawable.welcome_03,
            R.drawable.welcome_04,
            R.drawable.welcome_05
    };
    private final int[] gameBackgrounds = {
            R.drawable.game_background_01,
            R.drawable.game_background_02,
            R.drawable.game_background_03,
            R.drawable.game_background_04,
            R.drawable.game_background_05
    };

    private String difficulty = "标准";
    private int[][] puzzle = new int[9][9];
    private int[][] solution = new int[9][9];
    private int[][] player = new int[9][9];
    private boolean[][][] notes = new boolean[9][9][10];
    private boolean[][] checkedWrongCells = new boolean[9][9];
    private final ArrayDeque<Snapshot> history = new ArrayDeque<Snapshot>();

    private int selectedDigit = 1;
    private int mode = MODE_RESULT;
    private boolean clearTool = false;
    private boolean completed = false;
    private boolean inGame = false;
    private long startTimeMillis = 0L;
    private int welcomeBackgroundRes = 0;
    private int gameBackgroundRes = 0;

    private SudokuBoardView boardView;
    private ImageView welcomeBackgroundView;
    private ImageView gameBackgroundView;
    private TextView timerText;
    private TextView statusText;
    private Button difficultyButton;
    private Button modeButton;
    private Button clearButton;
    private final DigitButtonView[] digitButtons = new DigitButtonView[10];

    private final Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            updateTimer();
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        difficultyGivens.put("简单", 47);
        difficultyGivens.put("标准", 35);
        difficultyGivens.put("大师", 23);

        Window window = getWindow();
        window.setStatusBarColor(Color.rgb(232, 248, 255));
        window.setNavigationBarColor(Color.WHITE);
        showWelcome();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (inGame) {
            confirmReturnHome();
        } else {
            confirmExitGame();
        }
    }

    private void showWelcome() {
        inGame = false;
        handler.removeCallbacks(timerRunnable);
        FrameLayout root = new FrameLayout(this);
        ImageView bg = new ImageView(this);
        welcomeBackgroundView = bg;
        bg.setScaleType(ImageView.ScaleType.CENTER_CROP);
        welcomeBackgroundRes = welcomeBackgroundRes == 0 ? randomOf(welcomeImages) : welcomeBackgroundRes;
        bg.setImageResource(welcomeBackgroundRes);
        root.addView(bg, new FrameLayout.LayoutParams(-1, -1));

        View veil = new View(this);
        veil.setBackgroundColor(Color.argb(96, 255, 255, 255));
        root.addView(veil, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        applySafePadding(content, dp(22), dp(42), dp(22), dp(36), false);
        root.addView(content, new FrameLayout.LayoutParams(-1, -1));
        addFloatingWallpaperButton(root, true);

        TextView title = new TextView(this);
        title.setText("V家数独");
        title.setTextColor(CYAN_DEEP);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 42);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        content.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView subtitle = new TextView(this);
        subtitle.setText("洛天依主题 · 9x9 随机唯一解");
        subtitle.setTextColor(Color.rgb(33, 68, 75));
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        subtitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subtitleLp = new LinearLayout.LayoutParams(-1, -2);
        subtitleLp.topMargin = dp(6);
        content.addView(subtitle, subtitleLp);

        View spacer = new View(this);
        content.addView(spacer, new LinearLayout.LayoutParams(1, 0, 1f));

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding(dp(20), dp(20), dp(20), dp(20));
        card.setBackground(roundRect(Color.argb(238, 255, 255, 255), dp(14), CYAN_SOFT, dp(1)));
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(-1, -2);
        cardLp.leftMargin = dp(10);
        cardLp.rightMargin = dp(10);
        content.addView(card, cardLp);

        TextView choose = new TextView(this);
        choose.setText("选择难度");
        choose.setTextColor(Color.rgb(31, 61, 66));
        choose.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        choose.setTypeface(Typeface.DEFAULT_BOLD);
        choose.setGravity(Gravity.CENTER);
        card.addView(choose, new LinearLayout.LayoutParams(-1, -2));

        final LinearLayout diffRow = new LinearLayout(this);
        diffRow.setOrientation(LinearLayout.HORIZONTAL);
        diffRow.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams diffRowLp = new LinearLayout.LayoutParams(-1, -2);
        diffRowLp.topMargin = dp(14);
        card.addView(diffRow, diffRowLp);

        final Button[] diffButtons = new Button[3];
        final String[] diffs = {"简单", "标准", "大师"};
        for (int i = 0; i < diffs.length; i++) {
            final String diff = diffs[i];
            Button btn = makeButton(diff, 13, true);
            diffButtons[i] = btn;
            btn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    difficulty = diff;
                    for (int j = 0; j < diffButtons.length; j++) {
                        stylePill(diffButtons[j], diffs[j].equals(difficulty));
                    }
                }
            });
            stylePill(btn, diff.equals(difficulty));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(44), 1f);
            lp.leftMargin = dp(4);
            lp.rightMargin = dp(4);
            diffRow.addView(btn, lp);
        }

        Button start = makeButton("开始演奏", 16, true);
        start.setTextColor(Color.rgb(6, 44, 49));
        start.setBackground(roundRect(CYAN, dp(10), Color.TRANSPARENT, 0));
        start.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startNewGame(difficulty);
            }
        });
        LinearLayout.LayoutParams startLp = new LinearLayout.LayoutParams(-1, dp(52));
        startLp.topMargin = dp(20);
        card.addView(start, startLp);

        setContentView(root);
    }

    private void startNewGame(String diff) {
        difficulty = diff;
        Toast.makeText(this, "正在生成唯一解题目...", Toast.LENGTH_SHORT).show();
        Puzzle generated = generatePuzzle(diff);
        puzzle = generated.puzzle;
        solution = generated.solution;
        player = new int[9][9];
        notes = new boolean[9][9][10];
        checkedWrongCells = new boolean[9][9];
        history.clear();
        selectedDigit = 1;
        mode = MODE_RESULT;
        clearTool = false;
        completed = false;
        startTimeMillis = System.currentTimeMillis();
        gameBackgroundRes = randomOf(gameBackgrounds);
        showGame();
    }

    private void showGame() {
        inGame = true;
        handler.removeCallbacks(timerRunnable);
        FrameLayout root = new FrameLayout(this);

        ImageView bg = new ImageView(this);
        gameBackgroundView = bg;
        bg.setScaleType(ImageView.ScaleType.CENTER_CROP);
        gameBackgroundRes = gameBackgroundRes == 0 ? randomOf(gameBackgrounds) : gameBackgroundRes;
        bg.setImageResource(gameBackgroundRes);
        bg.setAlpha(0.78f);
        root.addView(bg, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        applySafePadding(content, dp(8), dp(10), dp(8), dp(8), true);
        content.setBackgroundColor(Color.argb(168, 242, 251, 255));
        root.addView(content, new FrameLayout.LayoutParams(-1, -1));

        content.addView(makeHeader(), new LinearLayout.LayoutParams(-1, dp(82)));

        boardView = new SudokuBoardView(this);
        LinearLayout.LayoutParams boardLp = new LinearLayout.LayoutParams(-1, -2);
        boardLp.topMargin = dp(6);
        boardLp.leftMargin = dp(2);
        boardLp.rightMargin = dp(2);
        content.addView(boardView, boardLp);

        LinearLayout first = new LinearLayout(this);
        first.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams firstLp = new LinearLayout.LayoutParams(-1, dp(78));
        firstLp.topMargin = dp(8);
        content.addView(first, firstLp);
        for (int digit = 1; digit <= 5; digit++) {
            addDigitButton(first, digit);
        }
        clearButton = makeButton("C", 19, true);
        clearButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clearTool = true;
                setStatus("清除工具：点格子清除最上层内容");
                updateControls();
                boardView.invalidate();
            }
        });
        addWeighted(first, clearButton);

        LinearLayout second = new LinearLayout(this);
        second.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams secondLp = new LinearLayout.LayoutParams(-1, dp(78));
        secondLp.topMargin = dp(8);
        content.addView(second, secondLp);
        for (int digit = 6; digit <= 9; digit++) {
            addDigitButton(second, digit);
        }
        modeButton = makeButton(modeText(), 12, true);
        modeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mode = mode == MODE_RESULT ? MODE_ASSIST : MODE_RESULT;
                clearTool = false;
                setStatus("已切换到 " + modeText());
                updateControls();
                boardView.invalidate();
            }
        });
        addWeighted(second, modeButton);

        Button assist = makeButton("辅助", 13, true);
        assist.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openAssistMenu();
            }
        });
        addWeighted(second, assist);

        LinearLayout tools = new LinearLayout(this);
        tools.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams toolsLp = new LinearLayout.LayoutParams(-1, dp(46));
        toolsLp.topMargin = dp(8);
        content.addView(tools, toolsLp);
        addSmallTool(tools, "撤销", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                undo();
            }
        });
        addSmallTool(tools, "提示", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                giveHint();
            }
        });
        addSmallTool(tools, "检查", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                checkBoard();
            }
        });
        addSmallTool(tools, "重开", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                restartCurrent();
            }
        });
        addSmallTool(tools, "新题", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                confirmNewGame();
            }
        });

        setContentView(root);
        setStatus("先选数字，再点棋盘");
        updateControls();
        updateTimer();
        handler.postDelayed(timerRunnable, 1000);
    }

    private void applySafePadding(final View view, final int left, final int top, final int right, final int bottom, final boolean includeBottom) {
        view.setPadding(left, top, right, bottom);
        view.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override
            public WindowInsets onApplyWindowInsets(View v, WindowInsets insets) {
                int safeLeft = Math.max(0, insets.getSystemWindowInsetLeft());
                int safeTop = Math.max(0, insets.getSystemWindowInsetTop());
                int safeRight = Math.max(0, insets.getSystemWindowInsetRight());
                int safeBottom = includeBottom ? Math.max(0, insets.getSystemWindowInsetBottom()) : 0;
                v.setPadding(left + safeLeft, top + safeTop, right + safeRight, bottom + safeBottom);
                return insets;
            }
        });
        view.requestApplyInsets();
    }

    private void addFloatingWallpaperButton(FrameLayout root, final boolean welcome) {
        FrameLayout layer = new FrameLayout(this);
        applySafePadding(layer, 0, dp(10), dp(14), 0, false);
        root.addView(layer, new FrameLayout.LayoutParams(-1, -1));

        Button button = makeButton("壁纸", 10, true);
        button.setTextColor(Color.rgb(33, 60, 66));
        button.setBackground(roundRect(Color.argb(236, 255, 255, 255), dp(10), CYAN_SOFT, dp(1)));
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (welcome) {
                    switchWelcomeWallpaper();
                } else {
                    switchGameWallpaper();
                }
            }
        });
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(58), dp(34), Gravity.TOP | Gravity.RIGHT);
        layer.addView(button, lp);
    }

    private void switchWelcomeWallpaper() {
        welcomeBackgroundRes = randomDifferent(welcomeImages, welcomeBackgroundRes);
        if (welcomeBackgroundView != null) {
            welcomeBackgroundView.setImageResource(welcomeBackgroundRes);
        }
    }

    private void switchGameWallpaper() {
        gameBackgroundRes = randomDifferent(gameBackgrounds, gameBackgroundRes);
        if (gameBackgroundView != null) {
            gameBackgroundView.setImageResource(gameBackgroundRes);
        }
        setStatus("已切换壁纸");
    }

    private int randomDifferent(int[] values, int current) {
        if (values.length == 0) {
            return current;
        }
        if (values.length == 1) {
            return values[0];
        }
        int next = current;
        for (int i = 0; i < 12 && next == current; i++) {
            next = randomOf(values);
        }
        if (next == current) {
            for (int i = 0; i < values.length; i++) {
                if (values[i] != current) {
                    return values[i];
                }
            }
        }
        return next;
    }

    private View makeHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(10), dp(8), dp(10), dp(8));
        header.setBackground(roundRect(Color.argb(226, 255, 255, 255), dp(10), Color.rgb(210, 238, 247), dp(1)));

        LinearLayout leftTimer = new LinearLayout(this);
        leftTimer.setOrientation(LinearLayout.VERTICAL);
        leftTimer.setGravity(Gravity.CENTER);
        timerText = new TextView(this);
        timerText.setTextColor(Color.rgb(34, 34, 34));
        timerText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        timerText.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        timerText.setGravity(Gravity.CENTER);
        leftTimer.addView(timerText, new LinearLayout.LayoutParams(-1, -2));
        header.addView(leftTimer, new LinearLayout.LayoutParams(0, -1, 1f));

        LinearLayout center = new LinearLayout(this);
        center.setOrientation(LinearLayout.VERTICAL);
        center.setGravity(Gravity.CENTER);
        TextView title = new TextView(this);
        title.setText("V家数独");
        title.setTextColor(CYAN_DEEP);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 21);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        center.addView(title, new LinearLayout.LayoutParams(-1, -2));

        statusText = new TextView(this);
        statusText.setSingleLine(true);
        statusText.setEllipsize(android.text.TextUtils.TruncateAt.END);
        statusText.setTextColor(Color.rgb(96, 114, 119));
        statusText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        statusText.setGravity(Gravity.CENTER);
        center.addView(statusText, new LinearLayout.LayoutParams(-1, -2));
        header.addView(center, new LinearLayout.LayoutParams(0, -1, 1.28f));

        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setGravity(Gravity.CENTER);

        difficultyButton = makeButton("难度 " + difficulty, 10, true);
        difficultyButton.setTextColor(Color.rgb(33, 60, 66));
        difficultyButton.setBackground(roundRect(CYAN_SOFT, dp(7), Color.TRANSPARENT, 0));
        difficultyButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openDifficultyDialog();
            }
        });
        LinearLayout.LayoutParams diffLp = new LinearLayout.LayoutParams(-1, dp(31));
        right.addView(difficultyButton, diffLp);

        Button wallpaperButton = makeButton("壁纸", 10, true);
        wallpaperButton.setTextColor(Color.rgb(33, 60, 66));
        wallpaperButton.setBackground(roundRect(Color.argb(236, 255, 255, 255), dp(7), CYAN_SOFT, dp(1)));
        wallpaperButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchGameWallpaper();
            }
        });
        LinearLayout.LayoutParams wallpaperLp = new LinearLayout.LayoutParams(-1, dp(27));
        wallpaperLp.topMargin = dp(4);
        right.addView(wallpaperButton, wallpaperLp);

        header.addView(right, new LinearLayout.LayoutParams(0, -1, 1f));
        return header;
    }

    private void addDigitButton(LinearLayout row, final int digit) {
        DigitButtonView button = new DigitButtonView(this, digit);
        digitButtons[digit] = button;
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectedDigit = digit;
                clearTool = false;
                setStatus("已选 " + digit + " · " + modeText());
                updateControls();
                boardView.invalidate();
            }
        });
        addWeighted(row, button);
    }

    private void addWeighted(LinearLayout row, View view) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1f);
        lp.leftMargin = dp(4);
        lp.rightMargin = dp(4);
        row.addView(view, lp);
    }

    private void addSmallTool(LinearLayout row, String text, View.OnClickListener listener) {
        Button button = makeButton(text, 10, true);
        button.setTextColor(Color.rgb(37, 70, 76));
        button.setBackground(roundRect(Color.WHITE, dp(8), Color.TRANSPARENT, 0));
        button.setOnClickListener(listener);
        addWeighted(row, button);
    }

    private Button makeButton(String text, int sp, boolean bold) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        button.setTextColor(Color.rgb(26, 51, 55));
        button.setGravity(Gravity.CENTER);
        button.setAllCaps(false);
        button.setIncludeFontPadding(false);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setPadding(dp(4), 0, dp(4), 0);
        if (bold) {
            button.setTypeface(Typeface.DEFAULT_BOLD);
        }
        button.setBackground(roundRect(Color.WHITE, dp(8), Color.TRANSPARENT, 0));
        return button;
    }

    private void stylePill(Button button, boolean selected) {
        button.setTextColor(selected ? Color.rgb(6, 44, 49) : Color.rgb(29, 51, 55));
        button.setBackground(roundRect(selected ? CYAN : Color.rgb(238, 246, 247), dp(8), Color.TRANSPARENT, 0));
    }

    private GradientDrawable roundRect(int color, float radius, int strokeColor, int strokeWidth) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        if (strokeWidth > 0) {
            drawable.setStroke(strokeWidth, strokeColor);
        }
        return drawable;
    }

    private void updateControls() {
        for (int digit = 1; digit <= 9; digit++) {
            DigitButtonView button = digitButtons[digit];
            if (button == null) {
                continue;
            }
            boolean selected = !clearTool && selectedDigit == digit;
            button.setState(selected, remainingDigitCount(digit));
        }
        if (clearButton != null) {
            clearButton.setTextColor(Color.rgb(16, 16, 16));
            clearButton.setBackground(roundRect(clearTool ? Color.rgb(170, 178, 184) : Color.rgb(218, 221, 224), dp(8), Color.TRANSPARENT, 0));
        }
        if (modeButton != null) {
            modeButton.setText(modeText());
            modeButton.setTextColor(Color.rgb(6, 44, 49));
            modeButton.setBackground(roundRect(mode == MODE_RESULT ? CYAN : GOLD_BUTTON, dp(8), Color.TRANSPARENT, 0));
        }
        if (difficultyButton != null) {
            difficultyButton.setText("难度 " + difficulty);
        }
    }

    private String modeText() {
        return mode == MODE_RESULT ? "结果数字" : "辅助数字";
    }

    private void setStatus(String text) {
        if (statusText != null) {
            statusText.setText(text);
        }
    }

    private void updateTimer() {
        if (timerText == null || startTimeMillis <= 0L) {
            return;
        }
        long elapsed = (System.currentTimeMillis() - startTimeMillis) / 1000L;
        long minutes = elapsed / 60L;
        long seconds = elapsed % 60L;
        timerText.setText(String.format(Locale.US, "%02d:%02d", minutes, seconds));
    }

    private void openDifficultyDialog() {
        final String[] items = {"简单", "标准", "大师"};
        new AlertDialog.Builder(this)
                .setTitle("切换难度")
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        final String requested = items[which];
                        if (requested.equals(difficulty)) {
                            return;
                        }
                        new AlertDialog.Builder(MainActivity.this)
                                .setTitle("生成新题")
                                .setMessage("生成一局新的 " + requested + " 题目？")
                                .setPositiveButton("生成", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        startNewGame(requested);
                                    }
                                })
                                .setNegativeButton("取消", null)
                                .show();
                    }
                })
                .show();
    }

    private void openAssistMenu() {
        final String[] items = {"填充全部辅助数字", "清空全部辅助数字", "清空结果数字", "全部清空"};
        new AlertDialog.Builder(this)
                .setTitle("辅助操作")
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which == 0) {
                            fillAllNotes();
                        } else if (which == 1) {
                            clearAllNotes();
                        } else if (which == 2) {
                            clearPlayerResults();
                        } else {
                            clearAllPlayerContent();
                        }
                    }
                })
                .show();
    }

    private void restartCurrent() {
        new AlertDialog.Builder(this)
                .setTitle("重新开始")
                .setMessage("恢复当前题目的初始状态？")
                .setPositiveButton("重开", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        pushHistory();
                        player = new int[9][9];
                        notes = new boolean[9][9][10];
                        checkedWrongCells = new boolean[9][9];
                        completed = false;
                        startTimeMillis = System.currentTimeMillis();
                        setStatus("已重新开始");
                        updateControls();
                        boardView.invalidate();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void confirmNewGame() {
        new AlertDialog.Builder(this)
                .setTitle("新题")
                .setMessage("按当前难度生成一局新题？")
                .setPositiveButton("生成", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        startNewGame(difficulty);
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void confirmReturnHome() {
        new AlertDialog.Builder(this)
                .setTitle("返回主界面")
                .setMessage("返回主界面会离开当前棋盘，确定返回吗？")
                .setPositiveButton("返回主界面", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        showWelcome();
                    }
                })
                .setNegativeButton("继续游戏", null)
                .show();
    }

    private void confirmExitGame() {
        new AlertDialog.Builder(this)
                .setTitle("退出游戏")
                .setMessage("确定要退出 V家数独 吗？")
                .setPositiveButton("退出", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        finish();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private boolean isFixed(int r, int c) {
        return puzzle[r][c] != 0;
    }

    private int valueAt(int r, int c) {
        return puzzle[r][c] != 0 ? puzzle[r][c] : player[r][c];
    }

    private int remainingDigitCount(int digit) {
        int used = 0;
        for (int r = 0; r < 9; r++) {
            for (int c = 0; c < 9; c++) {
                if (valueAt(r, c) == digit) {
                    used++;
                }
            }
        }
        return Math.max(0, 9 - used);
    }

    private void applyCellAction(int r, int c) {
        if (isFixed(r, c)) {
            setStatus("题目数字不可修改");
            return;
        }
        boolean changed = false;
        if (clearTool) {
            if (player[r][c] != 0) {
                pushHistory();
                player[r][c] = 0;
                changed = true;
                setStatus("已清除结果数字，辅助数字重新显示");
            } else if (hasAnyNote(r, c)) {
                pushHistory();
                for (int n = 1; n <= 9; n++) {
                    notes[r][c][n] = false;
                }
                changed = true;
                setStatus("已清除该格辅助数字");
            } else {
                setStatus("这个格子已经是空的");
            }
        } else if (mode == MODE_RESULT) {
            pushHistory();
            if (player[r][c] == selectedDigit) {
                player[r][c] = 0;
                setStatus("已清除结果数字 " + selectedDigit);
            } else {
                player[r][c] = selectedDigit;
                setStatus("已填入结果数字 " + selectedDigit);
            }
            changed = true;
        } else {
            if (player[r][c] != 0) {
                setStatus("该格已有结果数字，辅助数字被冻结");
                return;
            }
            pushHistory();
            notes[r][c][selectedDigit] = !notes[r][c][selectedDigit];
            setStatus((notes[r][c][selectedDigit] ? "已添加辅助数字 " : "已移除辅助数字 ") + selectedDigit);
            changed = true;
        }
        if (changed) {
            checkedWrongCells = new boolean[9][9];
            updateControls();
            boardView.invalidate();
            checkCompletion();
        }
    }

    private boolean hasAnyNote(int r, int c) {
        for (int n = 1; n <= 9; n++) {
            if (notes[r][c][n]) {
                return true;
            }
        }
        return false;
    }

    private void pushHistory() {
        history.addLast(new Snapshot(copyGrid(player), copyNotes(notes)));
        while (history.size() > 200) {
            history.removeFirst();
        }
    }

    private void undo() {
        if (history.isEmpty()) {
            setStatus("没有可撤销的操作");
            return;
        }
        Snapshot snapshot = history.removeLast();
        player = copyGrid(snapshot.player);
        notes = copyNotes(snapshot.notes);
        checkedWrongCells = new boolean[9][9];
        setStatus("已撤销一步");
        updateControls();
        boardView.invalidate();
    }

    private void fillAllNotes() {
        pushHistory();
        boolean changed = false;
        for (int r = 0; r < 9; r++) {
            for (int c = 0; c < 9; c++) {
                if (isFixed(r, c) || player[r][c] != 0) {
                    continue;
                }
                boolean[] candidates = candidatesFor(r, c);
                for (int n = 1; n <= 9; n++) {
                    if (notes[r][c][n] != candidates[n]) {
                        notes[r][c][n] = candidates[n];
                        changed = true;
                    }
                }
            }
        }
        if (!changed && !history.isEmpty()) {
            history.removeLast();
            setStatus("辅助数字无需更新");
        } else {
            setStatus("已填充全部辅助数字");
        }
        boardView.invalidate();
    }

    private boolean[] candidatesFor(int r, int c) {
        boolean[] used = new boolean[10];
        if (valueAt(r, c) != 0) {
            return new boolean[10];
        }
        for (int i = 0; i < 9; i++) {
            used[valueAt(r, i)] = true;
            used[valueAt(i, c)] = true;
        }
        int br = (r / 3) * 3;
        int bc = (c / 3) * 3;
        for (int rr = br; rr < br + 3; rr++) {
            for (int cc = bc; cc < bc + 3; cc++) {
                used[valueAt(rr, cc)] = true;
            }
        }
        boolean[] result = new boolean[10];
        for (int n = 1; n <= 9; n++) {
            result[n] = !used[n];
        }
        return result;
    }

    private void clearAllNotes() {
        boolean any = false;
        for (int r = 0; r < 9; r++) {
            for (int c = 0; c < 9; c++) {
                any = any || hasAnyNote(r, c);
            }
        }
        if (!any) {
            setStatus("没有辅助数字可清空");
            return;
        }
        pushHistory();
        notes = new boolean[9][9][10];
        setStatus("已清空全部辅助数字");
        boardView.invalidate();
    }

    private void clearPlayerResults() {
        boolean any = false;
        for (int r = 0; r < 9; r++) {
            for (int c = 0; c < 9; c++) {
                any = any || player[r][c] != 0;
            }
        }
        if (!any) {
            setStatus("没有玩家结果数字可清空");
            return;
        }
        pushHistory();
        player = new int[9][9];
        setStatus("已清空结果数字，辅助数字保留");
        updateControls();
        boardView.invalidate();
    }

    private void clearAllPlayerContent() {
        new AlertDialog.Builder(this)
                .setTitle("全部清空")
                .setMessage("清空玩家填写的结果数字和辅助数字？题目数字会保留。")
                .setPositiveButton("清空", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        pushHistory();
                        player = new int[9][9];
                        notes = new boolean[9][9][10];
                        setStatus("已全部清空，题目数字保留");
                        updateControls();
                        boardView.invalidate();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void giveHint() {
        List<int[]> cells = new ArrayList<int[]>();
        for (int r = 0; r < 9; r++) {
            for (int c = 0; c < 9; c++) {
                if (!isFixed(r, c) && valueAt(r, c) != solution[r][c]) {
                    cells.add(new int[]{r, c});
                }
            }
        }
        if (cells.isEmpty()) {
            setStatus("已经没有需要提示的格子");
            return;
        }
        int[] cell = cells.get(random.nextInt(cells.size()));
        pushHistory();
        player[cell[0]][cell[1]] = solution[cell[0]][cell[1]];
        checkedWrongCells = new boolean[9][9];
        setStatus("提示：第 " + (cell[0] + 1) + " 行第 " + (cell[1] + 1) + " 列是 " + solution[cell[0]][cell[1]]);
        updateControls();
        boardView.invalidate();
        checkCompletion();
    }

    private void checkBoard() {
        checkedWrongCells = new boolean[9][9];
        boolean[][] conflicts = conflictCells();
        int count = 0;
        for (int r = 0; r < 9; r++) {
            for (int c = 0; c < 9; c++) {
                boolean wrong = player[r][c] != 0 && player[r][c] != solution[r][c];
                if (wrong || conflicts[r][c]) {
                    checkedWrongCells[r][c] = true;
                    count++;
                }
            }
        }
        setStatus(count > 0 ? "检查：发现 " + count + " 个需要留意的格子" : "检查：目前没有发现错误");
        boardView.invalidate();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                checkedWrongCells = new boolean[9][9];
                if (boardView != null) {
                    boardView.invalidate();
                }
            }
        }, 2600);
    }

    private void checkCompletion() {
        if (completed) {
            return;
        }
        for (int r = 0; r < 9; r++) {
            for (int c = 0; c < 9; c++) {
                if (valueAt(r, c) != solution[r][c]) {
                    return;
                }
            }
        }
        completed = true;
        updateTimer();
        new AlertDialog.Builder(this)
                .setTitle("完成演出")
                .setMessage("恭喜通关！\n难度：" + difficulty + "\n用时：" + timerText.getText())
                .setPositiveButton("好", null)
                .show();
    }

    private boolean[][] conflictCells() {
        boolean[][] conflicts = new boolean[9][9];
        for (int r = 0; r < 9; r++) {
            int[][] cells = new int[9][2];
            for (int c = 0; c < 9; c++) {
                cells[c][0] = r;
                cells[c][1] = c;
            }
            scanConflicts(cells, conflicts);
        }
        for (int c = 0; c < 9; c++) {
            int[][] cells = new int[9][2];
            for (int r = 0; r < 9; r++) {
                cells[r][0] = r;
                cells[r][1] = c;
            }
            scanConflicts(cells, conflicts);
        }
        for (int br = 0; br < 9; br += 3) {
            for (int bc = 0; bc < 9; bc += 3) {
                int[][] cells = new int[9][2];
                int index = 0;
                for (int r = br; r < br + 3; r++) {
                    for (int c = bc; c < bc + 3; c++) {
                        cells[index][0] = r;
                        cells[index][1] = c;
                        index++;
                    }
                }
                scanConflicts(cells, conflicts);
            }
        }
        return conflicts;
    }

    private void scanConflicts(int[][] cells, boolean[][] conflicts) {
        int[] counts = new int[10];
        for (int i = 0; i < cells.length; i++) {
            int value = valueAt(cells[i][0], cells[i][1]);
            if (value != 0) {
                counts[value]++;
            }
        }
        for (int i = 0; i < cells.length; i++) {
            int value = valueAt(cells[i][0], cells[i][1]);
            if (value != 0 && counts[value] > 1) {
                conflicts[cells[i][0]][cells[i][1]] = true;
            }
        }
    }

    private Puzzle generatePuzzle(String diff) {
        int targetGivens = difficultyGivens.containsKey(diff) ? difficultyGivens.get(diff) : 34;
        int attempts = "大师".equals(diff) ? 5 : ("标准".equals(diff) ? 3 : 2);
        Puzzle best = null;
        int bestScore = "简单".equals(diff) ? Integer.MAX_VALUE : Integer.MIN_VALUE;
        int standardTargetScore = 88;

        for (int attempt = 0; attempt < attempts; attempt++) {
            int[][] fullSolution = generateSolution();
            int[][] puzzleGrid = digSymmetricPuzzle(fullSolution, targetGivens);
            int score = ratePuzzle(puzzleGrid, fullSolution);
            boolean better;
            if ("简单".equals(diff)) {
                better = score < bestScore;
            } else if ("标准".equals(diff)) {
                better = best == null || Math.abs(score - standardTargetScore) < Math.abs(bestScore - standardTargetScore);
            } else {
                better = score > bestScore;
            }
            if (better) {
                best = new Puzzle(puzzleGrid, fullSolution);
                bestScore = score;
            }
        }
        return best;
    }

    private int[][] digSymmetricPuzzle(int[][] fullSolution, int targetGivens) {
        int[][] puzzleGrid = copyGrid(fullSolution);
        List<int[]> groups = new ArrayList<int[]>();
        boolean[][] seen = new boolean[9][9];
        for (int r = 0; r < 9; r++) {
            for (int c = 0; c < 9; c++) {
                if (seen[r][c]) {
                    continue;
                }
                int mr = 8 - r;
                int mc = 8 - c;
                seen[r][c] = true;
                seen[mr][mc] = true;
                groups.add(new int[]{r, c, mr, mc});
            }
        }
        Collections.shuffle(groups, random);
        int givens = 81;
        while (!groups.isEmpty() && givens > targetGivens) {
            int[] group = groups.remove(groups.size() - 1);
            int r1 = group[0];
            int c1 = group[1];
            int r2 = group[2];
            int c2 = group[3];
            int removeCount = (r1 == r2 && c1 == c2) ? 1 : 2;
            if (givens - removeCount < targetGivens) {
                continue;
            }
            int old1 = puzzleGrid[r1][c1];
            int old2 = puzzleGrid[r2][c2];
            puzzleGrid[r1][c1] = 0;
            puzzleGrid[r2][c2] = 0;
            int[][] test = copyGrid(puzzleGrid);
            if (solveCount(test, 2) == 1) {
                givens -= removeCount;
            } else {
                puzzleGrid[r1][c1] = old1;
                puzzleGrid[r2][c2] = old2;
            }
        }
        return puzzleGrid;
    }

    private int ratePuzzle(int[][] puzzleGrid, int[][] fullSolution) {
        int[][] work = copyGrid(puzzleGrid);
        int score = countEmptyCells(work);
        int advancedSteps = 0;
        int guard = 0;
        while (countEmptyCells(work) > 0 && guard < 90) {
            guard++;
            int[] naked = findNakedSingle(work);
            if (naked != null) {
                work[naked[0]][naked[1]] = naked[2];
                score += 1;
                continue;
            }
            int[] hidden = findHiddenSingle(work);
            if (hidden != null) {
                work[hidden[0]][hidden[1]] = hidden[2];
                score += 3;
                continue;
            }
            int[] hard = findFewestCandidateCell(work);
            if (hard == null) {
                return score + 1000;
            }
            work[hard[0]][hard[1]] = fullSolution[hard[0]][hard[1]];
            advancedSteps++;
            score += 12 + hard[2] * 3 + advancedSteps * 2;
        }
        return score + advancedSteps * 10;
    }

    private int countEmptyCells(int[][] grid) {
        int count = 0;
        for (int r = 0; r < 9; r++) {
            for (int c = 0; c < 9; c++) {
                if (grid[r][c] == 0) {
                    count++;
                }
            }
        }
        return count;
    }

    private int[] findNakedSingle(int[][] grid) {
        for (int r = 0; r < 9; r++) {
            for (int c = 0; c < 9; c++) {
                if (grid[r][c] != 0) {
                    continue;
                }
                int mask = candidateMaskForGrid(grid, r, c);
                if (Integer.bitCount(mask) == 1) {
                    return new int[]{r, c, singleDigit(mask)};
                }
            }
        }
        return null;
    }

    private int[] findHiddenSingle(int[][] grid) {
        for (int r = 0; r < 9; r++) {
            int[][] cells = new int[9][2];
            for (int c = 0; c < 9; c++) {
                cells[c][0] = r;
                cells[c][1] = c;
            }
            int[] found = scanHiddenSingle(grid, cells);
            if (found != null) {
                return found;
            }
        }
        for (int c = 0; c < 9; c++) {
            int[][] cells = new int[9][2];
            for (int r = 0; r < 9; r++) {
                cells[r][0] = r;
                cells[r][1] = c;
            }
            int[] found = scanHiddenSingle(grid, cells);
            if (found != null) {
                return found;
            }
        }
        for (int br = 0; br < 9; br += 3) {
            for (int bc = 0; bc < 9; bc += 3) {
                int[][] cells = new int[9][2];
                int index = 0;
                for (int r = br; r < br + 3; r++) {
                    for (int c = bc; c < bc + 3; c++) {
                        cells[index][0] = r;
                        cells[index][1] = c;
                        index++;
                    }
                }
                int[] found = scanHiddenSingle(grid, cells);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private int[] scanHiddenSingle(int[][] grid, int[][] cells) {
        for (int n = 1; n <= 9; n++) {
            int count = 0;
            int lastR = -1;
            int lastC = -1;
            int bit = 1 << n;
            for (int i = 0; i < cells.length; i++) {
                int r = cells[i][0];
                int c = cells[i][1];
                if (grid[r][c] == 0 && (candidateMaskForGrid(grid, r, c) & bit) != 0) {
                    count++;
                    lastR = r;
                    lastC = c;
                }
            }
            if (count == 1) {
                return new int[]{lastR, lastC, n};
            }
        }
        return null;
    }

    private int[] findFewestCandidateCell(int[][] grid) {
        int bestOptions = 10;
        int bestR = -1;
        int bestC = -1;
        for (int r = 0; r < 9; r++) {
            for (int c = 0; c < 9; c++) {
                if (grid[r][c] != 0) {
                    continue;
                }
                int options = Integer.bitCount(candidateMaskForGrid(grid, r, c));
                if (options == 0) {
                    return null;
                }
                if (options < bestOptions) {
                    bestOptions = options;
                    bestR = r;
                    bestC = c;
                }
            }
        }
        return bestR < 0 ? null : new int[]{bestR, bestC, bestOptions};
    }

    private int candidateMaskForGrid(int[][] grid, int row, int col) {
        int mask = 0;
        for (int n = 1; n <= 9; n++) {
            mask |= 1 << n;
        }
        for (int c = 0; c < 9; c++) {
            int n = grid[row][c];
            if (n != 0) {
                mask &= ~(1 << n);
            }
        }
        for (int r = 0; r < 9; r++) {
            int n = grid[r][col];
            if (n != 0) {
                mask &= ~(1 << n);
            }
        }
        int br = row / 3 * 3;
        int bc = col / 3 * 3;
        for (int r = br; r < br + 3; r++) {
            for (int c = bc; c < bc + 3; c++) {
                int n = grid[r][c];
                if (n != 0) {
                    mask &= ~(1 << n);
                }
            }
        }
        return mask;
    }

    private int singleDigit(int mask) {
        for (int n = 1; n <= 9; n++) {
            if ((mask & (1 << n)) != 0) {
                return n;
            }
        }
        return 0;
    }

    private int[][] generateSolution() {
        List<Integer> rowGroups = shuffled012();
        List<Integer> colGroups = shuffled012();
        List<Integer> rows = new ArrayList<Integer>();
        List<Integer> cols = new ArrayList<Integer>();
        for (int gIndex = 0; gIndex < rowGroups.size(); gIndex++) {
            List<Integer> inner = shuffled012();
            for (int i = 0; i < inner.size(); i++) {
                rows.add(rowGroups.get(gIndex) * 3 + inner.get(i));
            }
        }
        for (int gIndex = 0; gIndex < colGroups.size(); gIndex++) {
            List<Integer> inner = shuffled012();
            for (int i = 0; i < inner.size(); i++) {
                cols.add(colGroups.get(gIndex) * 3 + inner.get(i));
            }
        }
        List<Integer> nums = new ArrayList<Integer>();
        for (int i = 1; i <= 9; i++) {
            nums.add(i);
        }
        Collections.shuffle(nums, random);
        int[][] grid = new int[9][9];
        for (int r = 0; r < 9; r++) {
            for (int c = 0; c < 9; c++) {
                grid[r][c] = nums.get(pattern(rows.get(r), cols.get(c)));
            }
        }
        return grid;
    }

    private List<Integer> shuffled012() {
        List<Integer> list = new ArrayList<Integer>();
        list.add(0);
        list.add(1);
        list.add(2);
        Collections.shuffle(list, random);
        return list;
    }

    private int pattern(int row, int col) {
        return (3 * (row % 3) + row / 3 + col) % 9;
    }

    private int solveCount(int[][] board, int limit) {
        int[] rows = new int[9];
        int[] cols = new int[9];
        int[] boxes = new int[9];
        List<int[]> empties = new ArrayList<int[]>();
        for (int r = 0; r < 9; r++) {
            for (int c = 0; c < 9; c++) {
                int n = board[r][c];
                if (n != 0) {
                    int bit = 1 << n;
                    int box = (r / 3) * 3 + c / 3;
                    if ((rows[r] & bit) != 0 || (cols[c] & bit) != 0 || (boxes[box] & bit) != 0) {
                        return 0;
                    }
                    rows[r] |= bit;
                    cols[c] |= bit;
                    boxes[box] |= bit;
                } else {
                    empties.add(new int[]{r, c});
                }
            }
        }
        int[] count = new int[]{0};
        solveBacktrack(board, rows, cols, boxes, empties, count, limit);
        return count[0];
    }

    private void solveBacktrack(int[][] board, int[] rows, int[] cols, int[] boxes, List<int[]> empties, int[] count, int limit) {
        if (count[0] >= limit) {
            return;
        }
        int bestIndex = -1;
        int bestMask = 0;
        int bestOptions = 10;
        int fullMask = 0;
        for (int n = 1; n <= 9; n++) {
            fullMask |= 1 << n;
        }
        for (int i = 0; i < empties.size(); i++) {
            int r = empties.get(i)[0];
            int c = empties.get(i)[1];
            if (board[r][c] != 0) {
                continue;
            }
            int box = (r / 3) * 3 + c / 3;
            int mask = fullMask & ~(rows[r] | cols[c] | boxes[box]);
            int options = Integer.bitCount(mask);
            if (options == 0) {
                return;
            }
            if (options < bestOptions) {
                bestOptions = options;
                bestIndex = i;
                bestMask = mask;
                if (options == 1) {
                    break;
                }
            }
        }
        if (bestIndex == -1) {
            count[0]++;
            return;
        }
        int r = empties.get(bestIndex)[0];
        int c = empties.get(bestIndex)[1];
        int box = (r / 3) * 3 + c / 3;
        List<Integer> choices = new ArrayList<Integer>();
        for (int n = 1; n <= 9; n++) {
            if ((bestMask & (1 << n)) != 0) {
                choices.add(n);
            }
        }
        Collections.shuffle(choices, random);
        for (int i = 0; i < choices.size(); i++) {
            int n = choices.get(i);
            int bit = 1 << n;
            board[r][c] = n;
            rows[r] |= bit;
            cols[c] |= bit;
            boxes[box] |= bit;
            solveBacktrack(board, rows, cols, boxes, empties, count, limit);
            rows[r] &= ~bit;
            cols[c] &= ~bit;
            boxes[box] &= ~bit;
            board[r][c] = 0;
            if (count[0] >= limit) {
                return;
            }
        }
    }

    private int[][] copyGrid(int[][] source) {
        int[][] copy = new int[9][9];
        for (int r = 0; r < 9; r++) {
            System.arraycopy(source[r], 0, copy[r], 0, 9);
        }
        return copy;
    }

    private boolean[][][] copyNotes(boolean[][][] source) {
        boolean[][][] copy = new boolean[9][9][10];
        for (int r = 0; r < 9; r++) {
            for (int c = 0; c < 9; c++) {
                System.arraycopy(source[r][c], 0, copy[r][c], 0, 10);
            }
        }
        return copy;
    }

    private int randomOf(int[] values) {
        return values[random.nextInt(values.length)];
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private static class Snapshot {
        final int[][] player;
        final boolean[][][] notes;

        Snapshot(int[][] player, boolean[][][] notes) {
            this.player = player;
            this.notes = notes;
        }
    }

    private static class Puzzle {
        final int[][] puzzle;
        final int[][] solution;

        Puzzle(int[][] puzzle, int[][] solution) {
            this.puzzle = puzzle;
            this.solution = solution;
        }
    }

    private class DigitButtonView extends View {
        private final int digit;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private boolean selected = false;
        private int remaining = 9;

        DigitButtonView(Activity context, int digit) {
            super(context);
            this.digit = digit;
            setClickable(true);
            setContentDescription("digit " + digit);
        }

        void setState(boolean selected, int remaining) {
            this.selected = selected;
            this.remaining = Math.max(0, remaining);
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float width = getWidth();
            float height = getHeight();
            boolean exhausted = remaining == 0;
            int bgColor = exhausted ? Color.rgb(216, 222, 227) : (selected ? CYAN : Color.WHITE);
            int textColor = exhausted ? Color.rgb(122, 133, 140) : (selected ? Color.rgb(6, 44, 49) : Color.rgb(26, 51, 55));

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(bgColor);
            RectF rect = new RectF(0, 0, width, height);
            canvas.drawRoundRect(rect, dp(8), dp(8), paint);
            if (selected) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(dp(3));
                paint.setColor(CYAN_DARK);
                canvas.drawRoundRect(new RectF(dp(1.5f), dp(1.5f), width - dp(1.5f), height - dp(1.5f)), dp(8), dp(8), paint);
            }

            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTypeface(Typeface.DEFAULT_BOLD);
            paint.setColor(textColor);
            paint.setTextSize(dp(24));
            Paint.FontMetrics mainFm = paint.getFontMetrics();
            float mainY = height / 2f - (mainFm.ascent + mainFm.descent) / 2f + dp(4);
            canvas.drawText(String.valueOf(digit), width / 2f, mainY, paint);

            paint.setStyle(Paint.Style.FILL);
            paint.setTypeface(Typeface.DEFAULT_BOLD);
            paint.setTextSize(dp(10));
            paint.setColor(textColor);
            paint.setTextAlign(Paint.Align.RIGHT);
            Paint.FontMetrics badgeFm = paint.getFontMetrics();
            float badgeY = dp(14) - (badgeFm.ascent + badgeFm.descent) / 2f;
            canvas.drawText(String.valueOf(remaining), width - dp(12), badgeY, paint);
        }
    }

    private class SudokuBoardView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private int pressedRow = -1;
        private int pressedCol = -1;

        SudokuBoardView(Activity context) {
            super(context);
            setBackgroundColor(Color.WHITE);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = MeasureSpec.getSize(widthMeasureSpec);
            int height = MeasureSpec.getSize(heightMeasureSpec);
            int size = width;
            if (MeasureSpec.getMode(heightMeasureSpec) != MeasureSpec.UNSPECIFIED && height > 0) {
                size = Math.min(width, height);
            }
            setMeasuredDimension(size, size);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float size = Math.min(getWidth(), getHeight());
            float cell = size / 9f;
            boolean[][] conflicts = conflictCells();

            for (int r = 0; r < 9; r++) {
                for (int c = 0; c < 9; c++) {
                    int fill = Color.WHITE;
                    int value = valueAt(r, c);
                    if (!clearTool && value == selectedDigit) {
                        fill = CYAN_SOFT;
                    }
                    if (conflicts[r][c] || checkedWrongCells[r][c]) {
                        fill = DANGER_BG;
                    }
                    if (pressedRow >= 0) {
                        boolean sameBox = r / 3 == pressedRow / 3 && c / 3 == pressedCol / 3;
                        if (sameBox) {
                            fill = Color.rgb(214, 240, 255);
                        }
                        if (r == pressedRow || c == pressedCol) {
                            fill = Color.rgb(231, 247, 255);
                        }
                        if (r == pressedRow && c == pressedCol) {
                            fill = GOLD;
                        }
                    }
                    paint.setStyle(Paint.Style.FILL);
                    paint.setColor(fill);
                    canvas.drawRect(c * cell, r * cell, (c + 1) * cell, (r + 1) * cell, paint);
                }
            }

            for (int r = 0; r < 9; r++) {
                for (int c = 0; c < 9; c++) {
                    int value = valueAt(r, c);
                    boolean conflict = conflicts[r][c] || checkedWrongCells[r][c];
                    if (value != 0) {
                        paint.setColor(conflict ? DANGER : (isFixed(r, c) ? Color.rgb(17, 17, 17) : CYAN_DARK));
                        paint.setTypeface(isFixed(r, c) ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
                        paint.setTextSize(cell * 0.62f);
                        paint.setTextAlign(Paint.Align.CENTER);
                        Paint.FontMetrics fm = paint.getFontMetrics();
                        float x = c * cell + cell / 2f;
                        float y = r * cell + cell / 2f - (fm.ascent + fm.descent) / 2f;
                        canvas.drawText(String.valueOf(value), x, y, paint);
                    } else if (hasAnyNote(r, c)) {
                        drawNotes(canvas, r, c, cell);
                    }
                }
            }

            paint.setStyle(Paint.Style.STROKE);
            for (int i = 0; i <= 9; i++) {
                paint.setColor(i % 3 == 0 ? Color.BLACK : LINE);
                paint.setStrokeWidth(i % 3 == 0 ? dp(3) : dp(1));
                float pos = i * cell;
                canvas.drawLine(pos, 0, pos, size, paint);
                canvas.drawLine(0, pos, size, pos, paint);
            }
        }

        private void drawNotes(Canvas canvas, int r, int c, float cell) {
            float sub = cell / 3f;
            paint.setTextSize(cell * 0.19f);
            paint.setTypeface(Typeface.DEFAULT_BOLD);
            paint.setTextAlign(Paint.Align.CENTER);
            Paint.FontMetrics fm = paint.getFontMetrics();
            for (int n = 1; n <= 9; n++) {
                if (!notes[r][c][n]) {
                    continue;
                }
                int nr = (n - 1) / 3;
                int nc = (n - 1) % 3;
                paint.setColor(mode == MODE_ASSIST && selectedDigit == n ? CYAN_DARK : NOTE);
                float x = c * cell + nc * sub + sub / 2f;
                float y = r * cell + nr * sub + sub / 2f - (fm.ascent + fm.descent) / 2f;
                canvas.drawText(String.valueOf(n), x, y, paint);
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            float size = Math.min(getWidth(), getHeight());
            if (size <= 0) {
                return true;
            }
            int col = (int) (event.getX() / (size / 9f));
            int row = (int) (event.getY() / (size / 9f));
            boolean inside = row >= 0 && row < 9 && col >= 0 && col < 9;
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                if (inside) {
                    pressedRow = row;
                    pressedCol = col;
                    invalidate();
                }
                return true;
            }
            if (event.getAction() == MotionEvent.ACTION_UP) {
                int oldRow = pressedRow;
                int oldCol = pressedCol;
                pressedRow = -1;
                pressedCol = -1;
                if (inside && oldRow == row && oldCol == col) {
                    applyCellAction(row, col);
                }
                invalidate();
                return true;
            }
            if (event.getAction() == MotionEvent.ACTION_CANCEL || event.getAction() == MotionEvent.ACTION_OUTSIDE) {
                pressedRow = -1;
                pressedCol = -1;
                invalidate();
                return true;
            }
            return true;
        }
    }
}
