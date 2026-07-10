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
        setupCaCert(binDir);
        try {
            ptmxFd = ParcelFileDescriptor.open(
                    new File("/dev/ptmx"),
                    ParcelFileDescriptor.MODE_READ_WRITE
            );
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        TermSession session = new TermSession();
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

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        LinearLayout.LayoutParams emulatorParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        );
        root.addView(emulatorView, emulatorParams);

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
        addKeyButton(keyRow, "CTRL+C", new Runnable() {
            public void run() { termSession.write(3); }
        });
        addKeyButton(keyRow, "CTRL+D", new Runnable() {
            public void run() { termSession.write(4); }
        });
        addKeyButton(keyRow, "CTRL+Z", new Runnable() {
            public void run() { termSession.write(26); }
        });
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

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    TermExec exec = new TermExec(shellPath);
                    exec.environment().put("PATH", newPath);
                    exec.environment().put("HOME", HOME_DIR);
                    exec.environment().put("PS1", "$PWD $ ");
                    exec.environment().put("ENV", HOME_DIR + "/.ashrc");
                    exec.environment().put("CURL_CA_BUNDLE", binDir.getAbsolutePath() + "/cacert.pem");
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

    /**
     * Extracts and installs busybox on first launch only. On subsequent
     * launches, detects the previous install and skips straight to
     * returning the existing bin folder.
     */
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

    /**
     * Generic helper: extracts a single executable asset (resolve, curl,
     * future tools) into the shared bin folder, once, and marks it
     * executable. Skips if already present from a previous launch.
     */
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

          /**
     * Extracts the CA certificate bundle (needed for curl's HTTPS
     * verification, since a static binary has no built-in trust store)
     * and sets CURL_CA_BUNDLE so curl finds it automatically.
     */
    private void setupCaCert(File binDir) {
        File certFile = new File(binDir, "cacert.pem");
        if (certFile.exists()) {
            return;
        }
        try {
            AssetManager assets = getAssets();
            InputStream in = assets.open("cacert.pem");
            OutputStream out = new FileOutputStream(certFile);
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            in.close();
            out.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
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
