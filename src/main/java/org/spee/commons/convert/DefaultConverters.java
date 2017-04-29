package org.spee.commons.convert;

import java.io.File;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.sql.Time;
import java.sql.Timestamp;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Currency;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

public class DefaultConverters {

	private DefaultConverters() {}

	@Converter
	public static String toString(Object o){
		return Objects.toString(o, null);
	}
	
	@Converter
	public static String toString(Enum<?> o){
		return o == null ? null : o.name();
	}
	
	@Converter
	public static char toCharacter(String s){
		return s == null ? null : s.charAt(0);
	}
	
	@Converter
	public static char toCharacter(Number s){
		return s == null ? null : (char)s.shortValue();
	}
	
	@Converter
	public static Boolean toBoolean(String s){
		return Boolean.valueOf(s);
	}
	
	@Converter
	public static Boolean toBoolean(byte s){
		if( s == (byte)1 ) return true;
		if( s == (byte)0 ) return false;
		return null;
	}
	
	@Converter
	public static URL toURL(String s) {
		try {
			return s == null ? null : new URL(s);
		} catch (MalformedURLException e) {
			throw new RuntimeException(e);
		}
	}
	
	@Converter
	public static URI toURI(String s){
		try {
			return s == null ? null : new URI(s);
		} catch (URISyntaxException e) {
			throw new RuntimeException(e);
		}
	}
	
	@Converter
    public static Calendar toCalendar(Date s){
		Calendar calendar = Calendar.getInstance();
		calendar.setTime(s);
		return calendar;
    }

	@Converter
    public static Calendar toCalendar(Long s){
		Calendar calendar = Calendar.getInstance();
		calendar.setTimeInMillis(s);
		return calendar;
    }
	
	@Converter
    public static Date toDate(Long s){
    	return s == null ? null : new Date(s);
    }
	
	@Converter
    public static Date toDate(java.sql.Date s){
    	return s;
    }
	
	@Converter
    public static Date toDate(Time s){
    	return s == null ? null : new Date(s.getTime());
    }
	
	@Converter
	public static Date toDate(Calendar s){
		return s == null ? null : new Date(s.getTimeInMillis());
	}
	
	@Converter
	public static java.sql.Date toSqlDate(Long s){
		return s == null ? null : new java.sql.Date(s);
	}
	
	@Converter
    public static java.sql.Date toSqlDate(Date s){
    	return s == null ? null : new java.sql.Date(s.getTime());
    }
	
	@Converter
    public static java.sql.Date toSqlDate(Calendar s){
    	return s == null ? null : new java.sql.Date( s.getTime().getTime());
    }	
	
	@Converter
	public static Time toTime(Long s){
		return s == null ? null : new Time(s);
	}

	@Converter
	public static Time toTime(Date s){
    	return s == null ? null : new Time(s.getTime());
    }
	
	@Converter
    public static Time toTime(Calendar s){
    	return s == null ? null : new Time(s.getTime().getTime());
    }
	
	@Converter
    public static Timestamp toTimestamp(Date s){
    	return s == null ? null : new Timestamp(s.getTime());
    }
	
	
	@Converter
    public static Double toDouble(String s){
    	return s == null ? null : Double.valueOf(s);
    }

	@Converter
	public static Double toDouble(Number s){
		return s == null ? null : s.doubleValue();
	}
	
	@Converter
    public static Short toShort(String s){
    	return s == null ? null : Short.valueOf(s);
    }
	
	@Converter
	public static Short toShort(Number s){
		return s == null ? null : s.shortValue();
	}
	
	@Converter
    public static Integer toInteger(String s){
    	return s == null ? null : Integer.valueOf(s);
    }
	
	@Converter
	public static Integer toInteger(Number s){
		return s == null ? null : s.intValue();
	}
	
	@Converter
	public static Integer toInteger(Enum<?> s){
		return s == null ? null : s.ordinal();
	}
	
	@Converter
    public static Long toLong(String s){
    	return s == null ? null : Long.valueOf(s);
    }
	
	@Converter
	public static Long toLong(Number s){
		return s == null ? null : s.longValue();
	}
		
	@Converter
	public static Byte toByte(Boolean s){
		return s ? (byte)1 : (byte)0;
	}
    
	@Converter
    public static UUID toUUID(String s){
    	return s == null ? null : UUID.fromString(s.trim());
    }
	
	@Converter
	public static UUID toUUID(byte[] s){
		return s == null ? null : UUID.nameUUIDFromBytes(s);
	}
	
	@Converter
	public static Currency toCurrency(String s){
		return s == null ? null : Currency.getInstance(s);
	}
	
	@Converter
	public static Charset toCharset(String s){
		return s == null ? null : Charset.forName(s);
	}

	@Converter
	public static Locale toLocale(String s){
		return s == null ? null : Locale.forLanguageTag(s);
	}

	@Converter
	public static BigInteger toBigInteger(String s){
		return s == null ? null : BigInteger.valueOf(toLong(s));
	}
	
	@Converter
	public static BigInteger toBigInteger(Number s){
		return s == null ? null : BigInteger.valueOf(s.longValue());
	}
	
	@Converter
	public static BigDecimal toBigDecimal(String s){
		return s == null ? null : BigDecimal.valueOf(toLong(s));
	}
	
	@Converter
	public static BigDecimal toBigDecimal(Number s){
		return s == null ? null : BigDecimal.valueOf(s.longValue());
	}

	@Converter
	public static File toFile(String s){
		return s == null ? null : new File(s);
	}
	
	@Converter
	public static File toFile(URI s){
		return s == null ? null : new File(s);
	}
	
	@Converter
	public static Inet4Address toInet4Address(String s){
		try {
			return (Inet4Address)Inet4Address.getByName(s);
		} catch (UnknownHostException e) {
			throw new RuntimeException(e);
		}
	}
	
	@Converter
	public static Inet4Address toInet4Address(byte[] s){
		try {
			return (Inet4Address)Inet4Address.getByAddress(s);
		} catch (UnknownHostException e) {
			throw new RuntimeException(e);
		}
	}
	
	@Converter
	public static Inet6Address toInet6Address(String s){
		try {
			return (Inet6Address)Inet6Address.getByName(s);
		} catch (UnknownHostException e) {
			throw new RuntimeException(e);
		}
	}
	
	@Converter
	public static Inet6Address toInet6Address(byte[] s){
		try {
			return (Inet6Address)Inet6Address.getByAddress(s);
		} catch (UnknownHostException e) {
			throw new RuntimeException(e);
		}
	}

	@Converter
	public static InetAddress toInetAddress(String s){
		try {
			return InetAddress.getByName(s);
		} catch (UnknownHostException e) {
			throw new RuntimeException(e);
		}
	}
	
	@Converter
	public static InetAddress toInetAddress(byte[] s){
		try {
			return InetAddress.getByAddress(s);
		} catch (UnknownHostException e) {
			throw new RuntimeException(e);
		}
	}
	
	
	
    /*
     * Register additional common "immutable" types
     */
//    converterFactory.registerConverter(new PassThroughConverter.Builtin(
//          URL.class,
//          URI.class,
//          UUID.class,
//          BigInteger.class,
//          Locale.class,
//          File.class,
//          Inet4Address.class,
//          Inet6Address.class,
//          InetSocketAddress.class
//            ));
//    /*
//     * Register additional common "cloneable" types
//     */
//    converterFactory.registerConverter(new CloneableConverter.Builtin(
//          Date.class,
//          Calendar.class,
//          XMLGregorianCalendar.class
//            ));
}
