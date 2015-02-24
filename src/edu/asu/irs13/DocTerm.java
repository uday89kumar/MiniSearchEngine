package edu.asu.irs13;

import org.apache.lucene.index.TermDocs;

public class DocTerm {

	public int docID;
	public int TF;
	public double IDF;
	public TermDocs termDoc;


	public DocTerm(int docID, int TF, double IDF, TermDocs termDoc){
		this.docID = docID;
		this.TF = TF;
		this.IDF = IDF;
		this.termDoc = termDoc;
	}

	public static void main(String[] args) {
		int[] array = { 4, 5, 6, 7, 8, 9, 12, 15, 16, 17, 18, 20, 22, 23, 24,
				27 };
		int i = 0;
		int size = array.length;

		StringBuilder sb = new StringBuilder();
		while (i < size - 1) {

			int end = findEnd(i, size - 1, array);
			if (end != i) {
				sb.append("" + i + ":" + end);
				i = end + 1;
			} else
				i++;
		}
		System.out.println("O/P:" + sb.toString());
	}

	private static int findEnd(int i, int size, int[] array) {

		int low = i, high = size;
		int mid;

		while (low < high) {
			mid = (low + high) / 2;

			if (mid - low == array[mid] - array[low]) {
				if (mid - low + 1 < array[mid + 1] - array[low])
					return mid;
				else
					low = mid + 1;
			} else
				high = mid - 1;
		}
		return low;
	}
}
