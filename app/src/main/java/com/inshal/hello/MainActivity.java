package com.inshal.hello;

import android.app.Activity;
import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Color;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.util.DisplayMetrics;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import jackpal.androidterm.TermExec;
import jackpal.androidterm.emulatorview.EmulatorView;
import jackpal.androidterm.emulatorview.TermSession;

public class MainActivity extends Activity {

    private static final String HOME_DIR = "/storage/emulated/0";

    private ParcelFileDescriptor ptmxFd;
    private volatile int shellPid = -1;
    private TermSession termSession;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final File binDir = setupBusybox();
        setupAsset(binDir, "resolve");
        setupAsset(binDir, "curl");
        final File terminfoDir = setupTerminfo();

        try {
            ptmxFd = ParcelFileDescriptor.open(
                    new File("/dev/ptmx"),
                    ParcelFileDescriptor.MODE_READ_WRITE
            );
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        final int ptyFd = ptmxFd.getFd();
        TermSession session = new TermSession() {
            @Override
            public void updateSize(int columns, int rows) {
                super.updateSize(columns, rows);
                TermExec.setPtyWindowSize(ptyFd, rows, columns);
            }
        };
        session.setTermIn(new FileInputStream(ptmxFd.getFileDescriptor()));
        session.setTermOut(new FileOutputStream(ptmxFd.getFileDescriptor()));
        termSession = session;

        DisplayMetrics metrics = getResources().getDisplayMetrics();
        final EmulatorView emulatorView = new EmulatorView(this, session, metrics);
        emulatorView.setFocusable(true);
        emulatorView.setFocusableInTouchMode(true);

        emulatorView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (imm != null) {
                        v.requestFocus();
                        imm.showSoftInput(v, InputMethodManager.SHOW_IMPLICIT);
                    }
                }
                return false;
            }
        });

        final LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        LinearLayout.LayoutParams emulatorParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        );
        root.addView(emulatorView, emulatorParams);

        // Force EmulatorView to properly re-measure and redraw whenever
        // the window resizes (e.g. keyboard opening/closing), fixing the
        // stale/floating-header glitch seen when the keyboard closes.
        root.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                emulatorView.updateSize(true);
            }
        });

        HorizontalScrollView keyScroll = new HorizontalScrollView(this);
        keyScroll.setBackgroundColor(Color.parseColor("#1a1a1a"));

        LinearLayout keyRow = new LinearLayout(this);
        keyRow.setOrientation(LinearLayout.HORIZONTAL);

        addKeyButton(keyRow, "ESC", new Runnable() {
            public void run() { termSession.write(27); }
        });
        addKeyButton(keyRow, "TAB", new Runnable() {
            public void run() { termSession.write(9); }
        });

        // Real toggle-style CTRL key, same behavior as Termux: tap to
        // arm it (button turns red), then the next keypress is sent as
        // a control character. Uses EmulatorView's own key listener via
        // the small public methods added to the library.
        final Button ctrlButton = new Button(this);
        ctrlButton.setText("CTRL");
        ctrlButton.setTextColor(Color.WHITE);
        ctrlButton.setBackgroundColor(Color.parseColor("#2a2a2a"));
        ctrlButton.setTextSize(12);
        ctrlButton.setPadding(24, 8, 24, 8);
        ctrlButton.setAllCaps(false);
        ctrlButton.setMinWidth(0);
        ctrlButton.setMinimumWidth(0);
        ctrlButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                emulatorView.toggleCtrlKey();
                if (emulatorView.isCtrlKeyActive()) {
                    ctrlButton.setBackgroundColor(Color.parseColor("#c0392b"));
                } else {
                    ctrlButton.setBackgroundColor(Color.parseColor("#2a2a2a"));
                }
            }
        });
        LinearLayout.LayoutParams ctrlParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        ctrlParams.setMargins(4, 4, 4, 4);
        keyRow.addView(ctrlButton, ctrlParams);

        addKeyButton(keyRow, "\u2191", new Runnable() {
            public void run() { termSession.write("\u001b[A"); }
        });
        addKeyButton(keyRow, "\u2193", new Runnable() {
            public void run() { termSession.write("\u001b[B"); }
        });
        addKeyButton(keyRow, "\u2190", new Runnable() {
            public void run() { termSession.write("\u001b[D"); }
        });
        addKeyButton(keyRow, "\u2192", new Runnable() {
            public void run() { termSession.write("\u001b[C"); }
        });
        addKeyButton(keyRow, "HOME", new Runnable() {
            public void run() { termSession.write("\u001b[H"); }
        });
        addKeyButton(keyRow, "END", new Runnable() {
            public void run() { termSession.write("\u001b[F"); }
        });
        addKeyButton(keyRow, "/", new Runnable() {
            public void run() { termSession.write("/"); }
        });
        addKeyButton(keyRow, "-", new Runnable() {
            public void run() { termSession.write("-"); }
        });
        addKeyButton(keyRow, "_", new Runnable() {
            public void run() { termSession.write("_"); }
        });
        addKeyButton(keyRow, "|", new Runnable() {
            public void run() { termSession.write("|"); }
        });

        keyScroll.addView(keyRow);
        LinearLayout.LayoutParams keyScrollParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        root.addView(keyScroll, keyScrollParams);

        setContentView(root);
        emulatorView.requestFocus();

        final String newPath = binDir.getAbsolutePath()
                + ":/system/bin:/system/xbin";
        final String shellPath = new File(binDir, "ash").getAbsolutePath();
        final String terminfoPath = terminfoDir.getAbsolutePath();

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    TermExec exec = new TermExec(shellPath);
                    exec.environment().put("PATH", newPath);
                    exec.environment().put("HOME", HOME_DIR);
                    exec.environment().put("PS1", "$PWD $ ");
                    exec.environment().put("ENV", HOME_DIR + "/.ashrc");
                    exec.environment().put("TERMINFO", terminfoPath);
                    exec.environment().put("TERM", "xterm");
                    shellPid = exec.start(ptmxFd);

                    Thread.sleep(300);
                    termSession.write("cd " + HOME_DIR + "\n");

                    TermExec.waitFor(shellPid);
                } catch (IOException | InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private void addKeyButton(LinearLayout parent, String label, final Runnable action) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextColor(Color.WHITE);
        button.setBackgroundColor(Color.parseColor("#2a2a2a"));
        button.setTextSize(12);
        button.setPadding(24, 8, 24, 8);
        button.setAllCaps(false);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                action.run();
            }
        });

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(4, 4, 4, 4);
        parent.addView(button, params);
    }

    private File setupBusybox() {
        File filesDir = getFilesDir();
        File busyboxFile = new File(filesDir, "busybox");
        File binDir = new File(filesDir, "bin");
        File installMarker = new File(binDir, "ls");

        if (installMarker.exists() && busyboxFile.exists()) {
            return binDir;
        }

        binDir.mkdirs();

        try {
            AssetManager assets = getAssets();
            InputStream in = assets.open("busybox");
            OutputStream out = new FileOutputStream(busyboxFile);
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            in.close();
            out.close();

            busyboxFile.setExecutable(true, false);

            ProcessBuilder pb = new ProcessBuilder(
                    busyboxFile.getAbsolutePath(),
                    "--install",
                    "-s",
                    binDir.getAbsolutePath()
            );
            pb.redirectErrorStream(true);
            Process installProcess = pb.start();
            installProcess.waitFor();

            File busyboxInBin = new File(binDir, "busybox");
            if (!busyboxInBin.exists()) {
                InputStream copyIn = new FileInputStream(busyboxFile);
                OutputStream copyOut = new FileOutputStream(busyboxInBin);
                byte[] copyBuffer = new byte[8192];
                int copyRead;
                while ((copyRead = copyIn.read(copyBuffer)) != -1) {
                    copyOut.write(copyBuffer, 0, copyRead);
                }
                copyIn.close();
                copyOut.close();
                busyboxInBin.setExecutable(true, false);
            }

        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }

        return binDir;
    }

    private void setupAsset(File binDir, String assetName) {
        File targetFile = new File(binDir, assetName);
        if (targetFile.exists()) {
            return;
        }

        try {
            AssetManager assets = getAssets();
            InputStream in = assets.open(assetName);
            OutputStream out = new FileOutputStream(targetFile);
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            in.close();
            out.close();

            targetFile.setExecutable(true, false);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private File setupTerminfo() {
        File filesDir = getFilesDir();
        File terminfoDir = new File(filesDir, "terminfo");
        File marker = new File(terminfoDir, "x/xterm");

        if (marker.exists()) {
            return terminfoDir;
        }

        String[] entries = { "x/xterm", "d/dumb", "l/linux" };
        try {
            AssetManager assets = getAssets();
            for (String entry : entries) {
                File targetFile = new File(terminfoDir, entry);
                targetFile.getParentFile().mkdirs();

                InputStream in = assets.open("terminfo/" + entry);
                OutputStream out = new FileOutputStream(targetFile);
                byte[] buffer = new byte[4096];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
                in.close();
                out.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return terminfoDir;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(getWindow().getDecorView().getWindowToken(), 0);
            }
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (shellPid != -1) {
            try {
                TermExec.sendSignal(shellPid, 9);
            } catch (Exception ignored) {
            }
        }
        if (ptmxFd != null) {
            try {
                ptmxFd.close();
            } catch (IOException ignored) {
            }
        }
    }
}
