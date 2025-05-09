package com.rtk.debug.util;

import java.lang.reflect.Array;
import java.util.List;

public class ArrayUtil {
	
	public static <T> int indexOf(T[] array, T object) {
		for (int i = 0; i < array.length; i++) {
			if (object.equals(array[i])) {
				return i;
			}
		}
		return -1;
	}
	
	// copied from Java 6 source
	
	 public static int[] copyOfRange(int[] original, int start, int end) {
         if (start <= end) {
             if (original.length >= start && 0 <= start) {
                 int length = end - start;
                 int copyLength = Math.min(length, original.length - start);
                 int[] copy = new int[length];
                 System.arraycopy(original, start, copy, 0, copyLength);
                 return copy;
             }
             throw new ArrayIndexOutOfBoundsException();
         }
         throw new IllegalArgumentException();
     }


    public static Object[] concatenate(Object[] first, Object[] second) {
        Object[] result = new Object[first.length + second.length];
        for (int i = 0; i < first.length; i++) {
          result[i] = first[i];
        }
        for (int i = 0; i < second.length; i++) {
          result[i + first.length] = second[i];
        }
        return result;
      }    

    public static int[] concatenate(int[] first, int[] second) {
      int[] result = new int[first.length + second.length];
      for (int i = 0; i < first.length; i++) {
        result[i] = first[i];
      }
      for (int i = 0; i < second.length; i++) {
        result[i + first.length] = second[i];
      }
      return result;
    }

    public static boolean[] concatenate(boolean[] first, boolean[] second) {
      boolean[] result = new boolean[first.length + second.length];
      for (int i = 0; i < first.length; i++) {
        result[i] = first[i];
      }
      for (int i = 0; i < second.length; i++) {
        result[i + first.length] = second[i];
      }
      return result;
    }
    
    public static boolean contains(int[] arr, int value) {
    	for (int i = 0; i < arr.length; i++) {
    		if (arr[i] == value) {
    			return true;
    		}
    	}
    	return false;
    }
    
    
	public static <T> T[] toArray(List<T> list, Class<T> clazz) {
		@SuppressWarnings("unchecked")
		T[] result = (T[]) Array.newInstance(clazz, list.size());
		for (int i = 0; i < list.size(); i++) {
			result[i] = list.get(i);
		}
		return result;
    }
    
}
