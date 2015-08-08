package tw.edu.ncku.eog;

public class QuickSelect {
    public static <E extends Comparable<? super E>> E getMedian(E[] arr) {
        return select(arr,arr.length/2+1);
    }

    private static <E extends Comparable<? super E>> int partition(E[] arr, int left, int right, int pivot) {
        E pivotVal = arr[pivot];
        swap(arr, pivot, right);
        int storeIndex = left;
        for (int i = left; i < right; i++) {
            if (arr[i].compareTo(pivotVal) < 0) {
                swap(arr, i, storeIndex);
                storeIndex++;
            }
        }
        swap(arr, right, storeIndex);
        return storeIndex;
    }

    public static <E extends Comparable<? super E>> E select(E[] arr, int n) {
        int left = 0;
        int right = arr.length - 1;
        while (right >= left) {
            int pivotIndex = partition(arr, left, right, medianOf3(arr,left,(left+right)/2,right));
            if (pivotIndex == n) {
                return arr[pivotIndex];
            } else if (pivotIndex < n) {
                left = pivotIndex + 1;
            } else {
                right = pivotIndex - 1;
            }
        }
        return null;
    }

    private static void swap(Object[] arr, int i1, int i2) {
        if (i1 != i2) {
            Object temp = arr[i1];
            arr[i1] = arr[i2];
            arr[i2] = temp;
        }
    }

    private static <E extends Comparable<? super E>> int medianOf3(E[] arr, int i, int ii, int iii){
        return arr[i].compareTo(arr[ii]) >= 0 ?
                (arr[i].compareTo(arr[iii]) <= 0? i : arr[ii].compareTo(arr[iii]) <= 0? ii : iii) :
                (arr[iii].compareTo(arr[i]) <= 0? i : arr[iii].compareTo(arr[ii]) >= 0? ii : iii);
    }

    public static float getMedian(float[] arr) {
        return select(arr,arr.length/2+1);
    }

    private static int partition(float[] arr, int left, int right, int pivot) {
        float pivotVal = arr[pivot];
        swap(arr, pivot, right);
        int storeIndex = left;
        for (int i = left; i < right; i++) {
            if (arr[i] < pivotVal) {
                swap(arr, i, storeIndex);
                storeIndex++;
            }
        }
        swap(arr, right, storeIndex);
        return storeIndex;
    }

    public static float select(float[] arr, int n) {
        int left = 0;
        int right = arr.length - 1;
        while (right >= left) {
            int pivotIndex = partition(arr, left, right, medianOf3(arr,left,(left+right)/2,right));
            if (pivotIndex == n) {
                return arr[pivotIndex];
            } else if (pivotIndex < n) {
                left = pivotIndex + 1;
            } else {
                right = pivotIndex - 1;
            }
        }
        return 0f;
    }

    private static void swap(float[] arr, int i1, int i2) {
        if (i1 != i2) {
            float temp = arr[i1];
            arr[i1] = arr[i2];
            arr[i2] = temp;
        }
    }

    private static int medianOf3(float[] arr, int i, int ii, int iii){
        return arr[i] >= arr[ii] ?
                (arr[i] <= arr[iii]? i : arr[ii] <= arr[iii]? ii : iii) :
                (arr[iii] <= arr[i]? i : arr[iii] <= arr[ii]? ii : iii);
    }
}
