import org.xerial.snappy.Snappy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.Deflater;
import java.util.zip.GZIPOutputStream;

/*
 * Bitmap compression rate benchmark on different encoding algorithm
 */
public class BitmapBenchmark {
    final static int MAX_BYTE = 128; // 2^7
    final static int VB = 1;
    final static int VB_PLUS = 2;
    final static int PFORDELTA = 3;
    int length;
    double ratio;
    int count = 0;
    byte[] bitmap;

    // Constructor
    public BitmapBenchmark(int length, double ratio) {
        this.length = length;
        this.ratio = ratio;
        bitmap = new byte[length];
        fillBitmap();
    }

    // Random generate the bitmap
    public void fillBitmap() {
        for(int i = 0; i < length; i++) {
            for(int j = 0; j < 8; j++) {
                boolean val = Math.random() <= ratio;
                if(val) {
                    bitmap[i] |= 1 << j;
                    count++;
                }
            }
        }
    }

    // Run length encoding
    public static byte[] RLE(byte[] bitmap, int rlEncoding) {
        int count = 0;
        ArrayList<Byte> list = new ArrayList<Byte>();
        ArrayList<Integer> deltas = new ArrayList<Integer>();
        int max = 0;
        for(int i = 0; i < bitmap.length; i++) {
            for(int j = 7; j >= 0; j--) {
                if((bitmap[i] & (1<<j)) == 0) {
                    count++;
                } else {
                    switch(rlEncoding) {
                        case VB:
                            list.addAll(variableLengthRLE(count));
                            break;
                        case VB_PLUS:
                            list.addAll(variableLengthPlusRLE(count));
                            break;
                        case PFORDELTA:
                            max = Math.max(count, max);
                            deltas.add(count);
                            if(deltas.size() == 128) {
                                list.addAll(pForDeltaRLE(deltas, max));
                                max = 0;
                                deltas.clear();
                            }
                            break;
                        default:
                            break;
                    }
                    count = 0;
                }
            }
        }
        if(rlEncoding==PFORDELTA)
            list.addAll(pForDeltaRLE(deltas, max));
        byte[] barr = new byte[list.size()];
        for(int i = 0; i < barr.length; i++)
            barr[i] = list.get(i).byteValue();
        return barr;
    }

    // Variable length (byte level) length encoding
    public static List<Byte> variableLengthRLE(int count) {
        ArrayList<Byte> list = new ArrayList<Byte>();
        byte b = (byte)(count % MAX_BYTE);
        while (count / MAX_BYTE > 0) {
            b |= 1 << 7;
            list.add(b);
            count /= MAX_BYTE;
            b = (byte)(count % MAX_BYTE);
        }
        list.add(b);
        return list;
    }


    // Variable length+ (byte level) length encoding
    public static List<Byte> variableLengthPlusRLE(int count) {
        ArrayList<Byte> list = new ArrayList<Byte>();
        if(count <= 254) {
            list.add((byte)count);
        } else {
            list.add((byte)255);
            list.addAll(variableLengthRLE(count-254));
        }
        return list;
    }

    // pForDelta length encoding
    public static List<Byte> pForDeltaRLE(List<Integer> deltas, int max) {
        ArrayList<Byte> list = new ArrayList<Byte>();
        int cohort = 32-Integer.numberOfLeadingZeros(max);

        int cp = 4;
        byte b = 0;
        int bp = 7;
        // Encode the cohort
        while(cp >= 0) {
            b |= (cohort & (1 << cp--)) >= 1 ? (1 << bp) : 0;
            bp-=1;
        }
        // Encode every delta in the length of cohort
        for(Integer d: deltas) {
            cp = cohort-1;
            while(cp >= 0) {
                if(bp<0) {
                    list.add(b);
                    b = 0;
                    bp = 7;

                }
                b |= (d&(1<< cp--))>=1? (1 << bp) : 0;
                bp-=1;
            }
        }
        list.add(b);
        return list;
    }

    // Roaring bitmap encoding
    public static double roaringStat(byte[] bitmap, int range, int offsetBit) {
        int max = 0;
        int count = 0;
        int totalCount=0;
        int index = 0;
        for(int i = 0; i < bitmap.length; i++) {
            for (int j = 7; j >= 0; j--) {
                if((bitmap[i] & (1<<j)) != 0) {
                    count+=1;
                    totalCount+=1;
                }
                index+=1;
                if((index >> offsetBit)!=((index-1) >> offsetBit)) {
                    max=Math.max(max, count);
                    count=0;
                }
            }
        }
        max=Math.max(max, count);
        return offsetBit+((32-Integer.numberOfLeadingZeros(max))*Math.pow(2, range-offsetBit)/totalCount);

    }

    public static void benchmark(byte[] bitmap, String name, int key_num) throws IOException {
//        System.out.println(name + ": "+ bitmap.length + " bits/key: " + bitmap.length*8.0/key_num);
        System.out.print(","+bitmap.length*8.0/key_num);

        ByteArrayOutputStream byteStream = new ByteArrayOutputStream(bitmap.length);
        GZIPOutputStream gzip = new GZIPOutputStream(byteStream);
        gzip.write(bitmap);
        gzip.close();
        byte[] compressedData = byteStream.toByteArray();
//        System.out.println(name + "+GZip: " + compressedData.length + " bits/key: " + compressedData.length*8.0/key_num);
        System.out.print(","+compressedData.length*8.0/key_num);

        Deflater compressor = new Deflater();
        compressor.setLevel(Deflater.BEST_COMPRESSION);
        compressor.setInput(bitmap);
        compressor.finish();
        ByteArrayOutputStream bos = new ByteArrayOutputStream(bitmap.length);

        // Compress the data
        byte[] buf = new byte[1024];
        while (!compressor.finished()) {
            int count = compressor.deflate(buf);
            bos.write(buf, 0, count);
        }
        try {
            bos.close();
        } catch (IOException e) {
        }

        // Get the compressed data
        byte[] crle = bos.toByteArray();
//        System.out.println(name + "+DFL: " + crle.length + " bits/key: " + crle.length*8.0/key_num);
        System.out.print(","+crle.length*8.0/key_num);
    }

    public static void main(String[] args) throws IOException {
        StringBuilder sb = new StringBuilder("Ratio,Key_Count,Snappy,GZip,VB,VB+GZip,VB+DFL,VB_PLUS,VB_PLUS+GZip," +
                "VB_PLUS+DFL,pForDelta,pForDelta+GZip,pForDelta+DFL");
        for(int i = 5; i<12; i++) {
            sb.append(String.format(",Roaring (Bucket: %d)", i));
        }
        System.out.println(sb.toString());

        for(int i = 1; i <= 10; i++) {
            BitmapBenchmark bit = new BitmapBenchmark(1<<20, 0.01*i);
//            System.out.println("Raw length in bytes: "+ bit.length+" Range: "+
//                    bit.length*8 +" Ratio: " + bit.ratio + " Key Count: " + bit.count);
            System.out.print(bit.ratio + "," + bit.count);

//            System.out.println("Snappy: " + Snappy.compress(bit.bitmap).length+ " ");
            System.out.print("," + Snappy.compress(bit.bitmap).length*8.0/bit.count);

            ByteArrayOutputStream gzipbyteStream = new ByteArrayOutputStream(bit.bitmap.length);
            GZIPOutputStream gzipgzip = new GZIPOutputStream(gzipbyteStream);
            gzipgzip.write(bit.bitmap);
            gzipgzip.close();
            byte[] compressedData = gzipbyteStream.toByteArray();
//            System.out.println("GZip: " + compressedData.length);
            System.out.print("," + compressedData.length*8.0/bit.count);

            byte[] vb = RLE(bit.bitmap, VB);
            benchmark(vb, "VB", bit.count);

            byte[] vb_plus = RLE(bit.bitmap, VB_PLUS);
            benchmark(vb_plus, "VB_PLUS", bit.count);

            byte[] pForDelta = RLE(bit.bitmap, PFORDELTA);
            benchmark(pForDelta, "pForDelta", bit.count);

            // Roaring bitmap
            for(int j = 5; j < 12; j++) {
//                System.out.println(String.format("Roaring (Bucket: %d): bits/key: %f",
//                        j, roaringStat(bit.bitmap, 24, j)));
                System.out.print(","+roaringStat(bit.bitmap, 24, j));
            }

            System.out.println();
        }
    }
}
