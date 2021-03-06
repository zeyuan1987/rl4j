package org.deeplearning4j.rl4j.learning;

import lombok.Getter;
import org.apache.commons.collections4.queue.CircularFifoQueue;
import org.bytedeco.javacv.*;
import org.datavec.image.loader.NativeImageLoader;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.compression.BasicNDArrayCompressor;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.indexing.NDArrayIndex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.io.IOException;

import static org.bytedeco.javacpp.avcodec.AV_CODEC_ID_H264;
import static org.bytedeco.javacpp.opencv_core.*;
import static org.bytedeco.javacpp.opencv_imgproc.*;

/**
 * @author rubenfiszel (ruben.fiszel@epfl.ch) on 7/27/16.
 *
 * An IHistoryProcessor implementation using JavaCV
 */
public class HistoryProcessor implements IHistoryProcessor {

    final private Logger log = LoggerFactory.getLogger("HistoryProcessor");
    @Getter
    final private Configuration conf;
    final private OpenCVFrameConverter openCVFrameConverter = new OpenCVFrameConverter.ToMat();
    private CircularFifoQueue<INDArray> history;
    private FFmpegFrameRecorder fmpegFrameRecorder = null;
    public static BasicNDArrayCompressor compressor = BasicNDArrayCompressor.getInstance().setDefaultCompression("UINT8");


    public HistoryProcessor(Configuration conf) {
        this.conf = conf;
        history = new CircularFifoQueue<>(conf.getHistoryLength());
    }


    public void add(INDArray obs) {
        INDArray processed = transform(obs);
        history.add(processed);
    }

    public void startMonitor(String filename) {
        stopMonitor();
        fmpegFrameRecorder = new FFmpegFrameRecorder(filename, 800, 600, 0);
        fmpegFrameRecorder.setVideoCodec(AV_CODEC_ID_H264);
        fmpegFrameRecorder.setFrameRate(35.0);
        fmpegFrameRecorder.setVideoBitrate(1000000);
        try {
            log.info("Started monitoring: " + filename);
            fmpegFrameRecorder.start();
        } catch (FrameRecorder.Exception e) {
            e.printStackTrace();
        }
    }

    public void stopMonitor() {
        if (fmpegFrameRecorder != null) {
            try {
                fmpegFrameRecorder.stop();
                fmpegFrameRecorder.release();
                log.info("Stopped monitoring");
            } catch (FrameRecorder.Exception e) {
                e.printStackTrace();
            }
        }
        fmpegFrameRecorder = null;
    }

    public boolean isMonitoring() {
        return fmpegFrameRecorder != null;
    }

    public void record(INDArray raw) {
        if (fmpegFrameRecorder != null) {
            int[] shape = raw.shape();
            Mat ocvmat = new Mat(shape[0], shape[1], CV_32FC(3), raw.data().pointer());
            Mat cvmat = new Mat(shape[0], shape[1], CV_8UC(3));
            ocvmat.convertTo(cvmat, CV_8UC(3));
            Frame frame = openCVFrameConverter.convert(cvmat);
            try {
                fmpegFrameRecorder.record(frame);
            } catch (FrameRecorder.Exception e) {
                e.printStackTrace();
            }
        }
    }

    public INDArray[] getHistory() {
        INDArray[] array = new INDArray[getConf().getHistoryLength()];
        for (int i = 0; i < conf.getHistoryLength(); i++) {
            array[i] = history.get(i);
        }
        return array;
    }


    private INDArray transform(INDArray raw) {
        int[] shape = raw.shape();
        Mat ocvmat = new Mat(shape[0], shape[1], CV_32FC(3), raw.data().pointer());
        Mat cvmat = new Mat(shape[0], shape[1], CV_8UC(3));
        ocvmat.convertTo(cvmat, CV_8UC(3));
        cvtColor(cvmat, cvmat, COLOR_RGB2GRAY);
        Mat resized = new Mat(conf.getRescaledHeight(), conf.getRescaledWidth(), CV_8UC(1));
        resize(cvmat, resized, new Size(conf.getRescaledHeight(), conf.getRescaledWidth()));
        //   show(resized);
        //   waitKP();
        //Crop by croppingHeight, crorpingHeight
        Mat cropped = resized.apply(new Rect(conf.getOffsetX(), conf.getOffsetY(), conf.getCroppingWidth(), conf.getCroppingHeight()));
        INDArray out = null;
        try {
            out = new NativeImageLoader(conf.getCroppingHeight(), conf.getCroppingWidth()).asMatrix(cropped);
        } catch (IOException e) {
            e.printStackTrace();
        }
        out = out.reshape(1, conf.getCroppingHeight(), conf.getCroppingWidth());
        INDArray compressed = compressor.compress(out);
        return compressed;
    }


    public void waitKP() {
        try {
            System.in.read();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void show(Mat m) {
        OpenCVFrameConverter.ToMat converter = new OpenCVFrameConverter.ToMat();
        CanvasFrame canvas = new CanvasFrame("LOL", 1);
        canvas.showImage(converter.convert(m));
    }


}
