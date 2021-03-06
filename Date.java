import java.util.EventListenerProxy;

//Created by: Aviv Buhbut id: 204445084

public class Date {

	private final int MIN_MONTH = 1;
	private final int MIN_YEAR = 1000;
	private final int MAX_YEAR = 9999;
	private int int_day;
	private int int_month;
	private int int_year;

	private final int MIN_DAY = 1;
	final int jan = 31;
	final int feblip = 29;
	final int febNotLip = 28;
	final int march = 31;
	final int april = 30;
	final int may = 30;
	final int june = 30;
	final int july = 31;
	final int aug = 31;
	final int sep = 30;
	final int oct = 31;
	final int nov = 30;
	final int dec = 31;

	final int[] arr = { jan, feblip, march, april, may, june, july, aug, sep, oct, nov, dec };

	
	/**
	 * checks how many days there are in the month that is passed as a parameter
	 * 
	 * @param arr - the array of each month
	 * @param month - the month needed to be checked
	 * 
	 * @return the days in the month that is passed as a parameter
	 */
	
	private int check30Or31Days(int[] arr, int month) {

		if (month == arr[0])
			return arr[0];
		if (month == arr[1])
			return arr[1];
		if (month == arr[2])
			return arr[2];
		if (month == arr[3])
			return arr[3];
		if (month == arr[4])
			return arr[4];
		if (month == arr[5])
			return arr[5];
		if (month == arr[6])
			return arr[6];
		if (month == arr[7])
			return arr[7];
		if (month == arr[8])
			return arr[8];
		if (month == arr[9])
			return arr[9];

		return arr[10];

	}



	
	/**
	 * creates a new Date object if the date is valid, otherwise creates the date 1/1/2000
	 * 
	 * @param day - the day in the month (1-31)
	 * @param month - the month in the year (1-12)
	 * @param  year - the year (4 digits)
	 * 
	 */
	
	public Date(int day, int month, int year) {

		if (checkLegalDate(day, month, year)) {

			this.int_day = day;
			this.int_month = month;
			this.int_year = year;

		} else {

			int_day = 1;
			int_month = 1;
			int_year = 2000;
		}

	}


	/**
	 * copy constructor
	 * 
	 * @param other - the date to be copied
	 * 
	 */

	public Date(Date other) {
		this.int_day = other.int_day;
		this.int_month = other.int_month;
		this.int_year = other.int_year;

	}


	
	/**
	 *  gets the year
	 * 
	 * @return the day
	 * 
	 */

	public int getDay() {

		return int_day;
	}


	/**
	 *  gets the month
	 * 
	 * @return the month
	 * 
	 */


	public int getMonth() {
		return int_month;
	}

	
	/**
	 *  gets the year
	 * 
	 * @return the year
	 * 
	 */

	public int getYear() {
		return int_year;
	}

	
	
	/**
	 *  sets the day (only if date remains valid)
	 * 
	 * @param dayToSet - the day value to be set
	 * 
	 */

	public int setDay(int dayToSet) {

		if (checkLegalDate(dayToSet, this.int_month, this.int_year))
			int_day = dayToSet;

		return int_day;

	}

	
	/**
	 *  sets the month (only if date remains valid)
	 * 
	 * @param setMonth - the month value to be set
	 * 
	 */
	
	
	public int setMonth(int monthToSet) {

		if (checkLegalDate(this.int_day, monthToSet, this.int_year))
			int_month = monthToSet;

		return this.int_month;

	}



	/**
	 *  sets the year (only if date remains valid)
	 * 
	 * @param setYear - the year value to be set
	 * 
	 */
	
	public int setYear(int yearToSet) {

		if (checkLegalDate(this.int_day, this.int_month, yearToSet))
			int_year = yearToSet;

		return this.int_year;

	}
	
	/**
	 *  checks if the date is legal
	 * 
	 * @param day - the day to check
     * @param month - the month to check
	 * @param year - the year to check
	 * 
	 * @return true if the date is legal
	 * 
	 */

	private boolean checkLegalDate(int day, int month, int year) {

		if (month < MIN_MONTH || month > arr.length || year < MIN_YEAR || year > MAX_YEAR || day < MIN_DAY
				|| day > arr[month - 1])
			return false;

		if (day > arr[month - 1])
			return false;

		// illegal day for not leap year
		if (month == 2 && !(year % 4 == 0) && day > arr[2] - 1)
			return false;

		return true;

	}
	/**
	 *  check if 2 dates are the same
	 * @param other - the date to compare this date to
	 * 
	 * @return true if the dates are the same
	 * 
	 */
	

	public boolean equals(Date other) {
		return (other.int_day == this.int_day && other.int_month == this.int_month && other.int_year == this.int_year);
	}

	
	
	/**
	 *  check if this date is before other date
	 * @param other - date to compare this date to
	 * 
	 * @return true if this date is before other date
	 * 
	 */
	public boolean before(Date other) {

		return (this.int_year < other.int_year
				|| ((this.int_year == other.int_year) && (this.int_month < other.int_month))
				|| ((this.int_year == other.int_year) && (this.int_month == other.int_month)
						&& (this.int_day < other.int_day)));

	}



	/**
	 *  check if this date is after other date
	 * @param other - date to compare this date to
	 * 
	 * @return true if this date is after other date
	 * 
	 */
	public boolean after(Date other) {
		return (!before(other));

	}



	/**
	 *  check if 2 dates are the same
	 * @param other - the date to compare this date tos
	 * 
	 * @return true if the dates are the same
	 * 
	 */
	
	
	public int difference(Date date) {

		return Math.abs(calculateDate(this.int_day, this.int_month, this.int_year)
				- calculateDate(date.int_day, date.int_month, date.int_year));

	}
	
	

	/**
	 *  check if 2 dates are the same
	 * @param day - the day of the date to calculate
	 * @param month - the month of the date to calculate
	 * @param year - the year of the date to calculate
	 * 
	 * 
	 */

	private int calculateDate(int day, int month, int year) {
		if (month < 3) {
			year--;
			month = month + 12;
		}
		return 365 * year + year / 4 - year / 100 + year / 400 + ((month + 1) * 306) / 10 + (day - 62);
	}

	/*
	 * returns a String that represents this date
	 * 
	 * @Overrides toString in class java.lang.Object
	 * 
	 * @returns String that represents this date in the following format: day/month/year for example: 02/03/1998
	 * 
	 */
	
	/**
	 *  returns a String that represents this date
	 * @Overrides toString in class java.lang.Object
	 * @return String that represents this date in the following format: day/month/year for example: 02/03/1998
	 * 
	 * 
	 */

	@Override
	public String toString() {

		if (this.int_month < 10 && this.int_day < 10)
			return "0" + this.int_day + "/0" + this.int_month + "/" + this.int_year;

		if (this.int_month < 10)
			return this.int_day + "/0" + this.int_month + "/" + this.int_year;
		if (this.int_day < 10)
			return "0" + this.int_day + "/" + this.int_month + "/" + this.int_year;

		return this.int_day + "/" + this.int_month + "/" + this.int_year;
	}

	/*
	 * calculate the date of tomorrow
	 * 
	 * @return the date of tomorrow
	 */
	
	/**
	 *  calculate the date of tomorrow
	 * @returnowing the date of tomorrow
	 * 
	 * 
	 */

	public Date tomorrow() {

		int updatedDay = 0;
		int updatedMonth = 0;
		int updatedYear = 0;

		if (checkLegalDate(this.int_day + 1, this.int_month, this.int_year)) {
			updatedDay = this.int_day + 1;
			updatedMonth = this.int_month;
			updatedYear = this.int_year;
		} else {

			// check year
			if (this.int_month == 12) {
				updatedDay = MIN_DAY;
				updatedMonth = MIN_MONTH;
				updatedYear = this.int_year + 1;

			} else {
				updatedYear = this.int_year;
				updatedDay = MIN_DAY;
			}

		}

		Date updatedDate = new Date(updatedDay, updatedMonth, updatedYear);

		return updatedDate;
	}


	/**
	 *  calculate the day of the week that this date occurs on 0-Saturday 1-Sunday 2-Monday etc.
	 * @return the day of the week that this date occurs on
	 * 
	 * 
	 */
	
	public int dayInWeek(Date date) {

		int m = this.int_month;

		int tempYear = this.int_year;

		if (this.int_month == 1) {
			tempYear--;
			m = 13;
		} else if (this.int_month == 2) {
			tempYear--;
			m = 14;
		}
		int C = tempYear / 100;
		int Y = tempYear % 100;

		return (this.int_day + (26 * (m + 1)) / 10 + Y + Y / 4 + C / 4 - 2 * C) % 7;

	}

}
