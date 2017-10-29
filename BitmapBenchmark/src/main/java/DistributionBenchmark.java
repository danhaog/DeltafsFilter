import java.util.ArrayList;

/*
 * run length distribution experiment benchmark
 */
public class DistributionBenchmark {
    public static void main(String[] args) {
        BitmapBenchmark bit = new BitmapBenchmark(1000000, 0.01);
        ArrayList<Integer> list = new ArrayList<Integer>();
        int count = 0;
        int sum = 0;
        int num = 0;
        for(int i = 0; i < bit.bitmap.length; i++) {
            for (int j = 7; j >= 0; j--) {
                if ((bit.bitmap[i] & (1 << j)) == 0) {
                    count++;
                } else {
                    System.out.print(count+",");
                    sum += count;
                    num += 1;
                    int index = count/16;
                    while(index >= list.size()) {
                        list.add(0);
                    }
                    list.set(index, list.get(index)+1);
                    count = 0;
                }
            }
        }
        System.out.print(count);
        sum += count;
        num += 1;
        int index = count/4;
        while(index >= list.size()) {
            list.add(0);
        }
        list.set(index, list.get(index)+1);
        System.out.println();
        System.out.println("Avg: " + sum/(num+0.0));
        for(int i = 0 ; i < list.size(); i++) {
            System.out.print((i+1)*16+",");
        }
        System.out.println();
        for(int i = 0 ; i < list.size(); i++) {
            System.out.print(list.get(i)+",");
        }
    }
}
