package com.inshal.hello;

import android.app.Activity;
import android.content.Context;
import android.content.res.AssetManager;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.util.DisplayMetrics;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;

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

    private ParcelFileDescriptor ptmxFd;
    private volatile int shellPid = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final File binDir = setupBusybox();

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

        DisplayMetrics metrics = getResources().getDisplayMetrics();
        final EmulatorView emulatorView = new EmulatorView(this, session, metrics);
        emulatorView.setFocusable(true);
        emulatorView.setFocusableInTouchMode(true);

        setContentView(emulatorView);
        emulatorView.requestFocus();

        getWindow().setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
        );

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

        final String newPath = binDir.getAbsolutePath()
                + ":/system/bin:/system/xbin";

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    TermExec exec = new TermExec("/system/bin/sh");
                    exec.environment().put("PATH", newPath);
                    exec.environment().put("HOME", getFilesDir().getAbsolutePath());
                    shellPid = exec.start(ptmxFd);
                    TermExec.waitFor(shellPid);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    /**
     * Copies busybox from assets into private storage, marks it executable,
     * and runs its self-install to generate symlinks for every applet
     * (ls, grep, vi, tar, etc.) into a bin/ folder.
     * Returns the bin/ folder that should be added to PATH.
     */
    private File setupBusybox() {
        File filesDir = getFilesDir();
        File busyboxFile = new File(filesDir, "busybox");
        File binDir = new File(filesDir, "bin");
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

        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }

        return binDir;
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
