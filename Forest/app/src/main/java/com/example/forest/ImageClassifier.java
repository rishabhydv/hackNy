/* Copyright 2017 The TensorFlow Authors. All Rights Reserved.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
==============================================================================*/

package com.example.forest;

import android.app.Activity;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.os.SystemClock;
import android.util.Log;

import org.tensorflow.lite.Interpreter;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

public class ImageClassifier {

  private static final String TAG = "TfLiteCameraDemo";
  private static final String MODEL_PATH = "tflite_model.tflite";
  private static final String LABEL_PATH = "tflite_dict.txt";
  private static final int RESULTS_TO_SHOW = 3;
  private static final int DIM_BATCH_SIZE = 1;

  private static final int DIM_PIXEL_SIZE = 3;

  private final int DIM_IMG_SIZE_X;
  private final int DIM_IMG_SIZE_Y;

  private static final int IMAGE_MEAN = 128;
  private static final float IMAGE_STD = 128.0f;


  /* Preallocated buffers for storing image data in. */
  private int[] intValues;
  private Interpreter tflite;
  private List<String> labelList;
  private ByteBuffer imgData = null;

  private static final int FILTER_STAGES = 3;
  private static final float FILTER_FACTOR = 0.4f;

  private byte[][] labelProbArray = null;
  private byte[][] filterLabelProbArray = null;

  private PriorityQueue<Map.Entry<String, Float>> sortedLabels =
      new PriorityQueue<>(
          RESULTS_TO_SHOW,
          new Comparator<Map.Entry<String, Float>>() {
            @Override
            public int compare(Map.Entry<String, Float> o1, Map.Entry<String, Float> o2) {
              return (o1.getValue()).compareTo(o2.getValue());
            }
          });

  /** Initializes an {@code ImageClassifier}. */
  ImageClassifier(Context context,Bitmap imageBitmap) throws IOException {
    tflite = new Interpreter(loadModelFile(context));
    labelList = loadLabelList(context);
    DIM_IMG_SIZE_X=imageBitmap.getWidth();
    DIM_IMG_SIZE_Y=imageBitmap.getHeight();
    intValues = new int[DIM_IMG_SIZE_X * DIM_IMG_SIZE_Y];
    imgData =
        ByteBuffer.allocateDirect(
            DIM_BATCH_SIZE * DIM_IMG_SIZE_X * DIM_IMG_SIZE_Y * DIM_PIXEL_SIZE);
    imgData.order(ByteOrder.nativeOrder());
    labelProbArray = new byte[1][labelList.size()];
    filterLabelProbArray = new byte[FILTER_STAGES][labelList.size()];
    Log.d(TAG, "Created a Tensorflow Lite Image Classifier.");
  }

  /** Classifies a frame from the preview stream. */
  String classifyFrame(Bitmap bitmap) {
    if (tflite == null) {
      Log.e(TAG, "Image classifier has not been initialized; Skipped.");
      return "Uninitialized Classifier.";
    }
    convertBitmapToByteBuffer(bitmap);
    // Here's where the magic happens!!!
    long startTime = SystemClock.uptimeMillis();
    tflite.run(imgData, labelProbArray);
    long endTime = SystemClock.uptimeMillis();
    Log.d(TAG, "Timecost to run model inference: " + Long.toString(endTime - startTime));

    // smooth the results
    //applyFilter();

    // print the results
    String textToShow = printTopKLabels();
    textToShow = Long.toString(endTime - startTime) + "ms" + textToShow;
    return textToShow;
  }

  void applyFilter(){
    int num_labels =  labelList.size();

    // Low pass filter `labelProbArray` into the first stage of the filter.
    for(int j=0; j<num_labels; ++j){
      filterLabelProbArray[0][j] += FILTER_FACTOR*(labelProbArray[0][j] -
                                                   filterLabelProbArray[0][j]);
    }
    // Low pass filter each stage into the next.
    for (int i=1; i<FILTER_STAGES; ++i){
      for(int j=0; j<num_labels; ++j){
        filterLabelProbArray[i][j] += FILTER_FACTOR*(
                filterLabelProbArray[i-1][j] -
                filterLabelProbArray[i][j]);

      }
    }

    // Copy the last stage filter output back to `labelProbArray`.
    for(int j=0; j<num_labels; ++j){
      labelProbArray[0][j] = filterLabelProbArray[FILTER_STAGES-1][j];
    }
  }

  public void close() {
    tflite.close();
    tflite = null;
  }

  private List<String> loadLabelList(Context activity) throws IOException {
    List<String> labelList = new ArrayList<String>();
    BufferedReader reader =
        new BufferedReader(new InputStreamReader(activity.getAssets().open(LABEL_PATH)));
    String line;
    while ((line = reader.readLine()) != null) {
      labelList.add(line);
    }
    reader.close();
    return labelList;
  }

  private MappedByteBuffer loadModelFile(Context activity) throws IOException {
    AssetFileDescriptor fileDescriptor = activity.getAssets().openFd(MODEL_PATH);
    FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
    FileChannel fileChannel = inputStream.getChannel();
    long startOffset = fileDescriptor.getStartOffset();
    long declaredLength = fileDescriptor.getDeclaredLength();
    return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
  }

  private void convertBitmapToByteBuffer(Bitmap bitmap) {

    try{
      if (imgData == null) {
        return;
      }
      imgData.rewind();
      bitmap.getPixels(intValues, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());
      // Convert the image to floating point.
      int pixel = 0;
      long startTime = SystemClock.uptimeMillis();
      for (int i = 0; i < DIM_IMG_SIZE_X; i++) {
        for (int j = 0; j < DIM_IMG_SIZE_Y; j++) {
          final int val = intValues[pixel];
          imgData.putFloat((((val >> 16) & 0xFF)));
          imgData.putFloat((((val >> 8) & 0xFF)));
          imgData.putFloat((((val) & 0xFF)));
          pixel++;
        }
      }
      long endTime = SystemClock.uptimeMillis();
      Log.d(TAG, "Timecost to put values into ByteBuffer: " + Long.toString(endTime - startTime));

    } catch (Exception ex){
      System.out.println(ex);
    }

  }

  private String printTopKLabels() {
    for (int i = 0; i < labelList.size(); ++i) {
      sortedLabels.add(
          new AbstractMap.SimpleEntry<>(labelList.get(i), (float)labelProbArray[0][i]));
      if (sortedLabels.size() > RESULTS_TO_SHOW) {
        sortedLabels.poll();
      }
    }
    String textToShow = "";
    final int size = sortedLabels.size();
    for (int i = 0; i < size; ++i) {
      Map.Entry<String, Float> label = sortedLabels.poll();
      textToShow = String.format("\n%s: %4.2f",label.getKey(),label.getValue()) + textToShow;
    }
    return textToShow;
  }
}