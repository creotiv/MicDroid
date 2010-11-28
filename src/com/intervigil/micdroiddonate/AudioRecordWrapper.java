/* AudioRecordWrapper.java
   An auto-tune app for Android

   Copyright (c) 2010 Ethan Chen

   This program is free software; you can redistribute it and/or modify
   it under the terms of the GNU General Public License as published by
   the Free Software Foundation; either version 2 of the License, or
   (at your option) any later version.

   This program is distributed in the hope that it will be useful,
   but WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
   GNU General Public License for more details.

   You should have received a copy of the GNU General Public License along
   with this program; if not, write to the Free Software Foundation, Inc.,
   51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
   
   This wrapper for AudioRecord inspired by work done by Peter Brinkmann 
   for PureData for Android.
 */

package com.intervigil.micdroiddonate;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.SynchronousQueue;

import android.content.Context;
import android.media.AudioRecord;
import android.os.Handler;
import android.os.Message;
import android.os.Process;

import com.intervigil.micdroiddonate.helper.AudioHelper;
import com.intervigil.micdroiddonate.model.Sample;

public class AudioRecordWrapper {

    private MicRecorder micRecorder;
    private AudioRecord audioRecord;
    private final BlockingQueue<Sample> queue;
    private final int bufferSize;

    public AudioRecordWrapper(Context context) 
            throws IllegalArgumentException {
        this.audioRecord = AudioHelper.getRecorder(context);
        this.queue = new SynchronousQueue<Sample>();
        this.bufferSize = AudioHelper.getRecorderBufferSize(context);
    }

    public synchronized void start() 
            throws IllegalStateException {
        audioRecord.startRecording();
        micRecorder = new MicRecorder();
        micRecorder.start();
    }

    public synchronized void stop() {
        if (micRecorder != null) {
            micRecorder.interrupt();
            try {
                micRecorder.join();
            } catch (InterruptedException e) {
            }
            micRecorder = null;
        }
        if (audioRecord != null) {
            audioRecord.stop();
        }
        if (queue != null) {
            queue.clear();
        }
    }

    public synchronized void cleanup() {
        stop();
        if (audioRecord != null) {
            audioRecord.release();
            audioRecord = null;
        }
    }

    public Sample poll() {
        return queue.poll();
    }

    public Sample take() throws InterruptedException {
        return queue.take();
    }

    private class MicRecorder extends Thread {

        @Override
        public void run() {
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO);

            Sample s = new Sample(new short[bufferSize], bufferSize);
            Sample t = new Sample(new short[bufferSize], bufferSize);

            while (!Thread.interrupted()) {
                s.bufferSize = audioRecord.read(s.buffer, 0, s.buffer.length);
                try {
                    queue.put(s);
                } catch (InterruptedException e) {
                    break;
                }

                Sample tmp = s;
                s = t;
                t = tmp;
            }
        }
    }
}