package org.spee.commons.convert.internals;

import java.lang.ref.WeakReference;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;

import org.spee.commons.convert.DefaultConverters;

public class XMLGregorianCalendarConverter {

	private static WeakReference<DatatypeFactory> threadFactory;
	
	private static DatatypeFactory getDatatypeFactory(){
		DatatypeFactory dtf = (threadFactory == null) ? null : threadFactory.get();
		if( dtf == null ){
			try {
				dtf = DatatypeFactory.newInstance();
				threadFactory = new WeakReference<DatatypeFactory>(dtf);
			} catch (DatatypeConfigurationException e) {
				throw new RuntimeException(e);
			}
		}
		return dtf;
	}
	
	protected static XMLGregorianCalendar createInstance(){
		return getDatatypeFactory().newXMLGregorianCalendar();
	}

    public static XMLGregorianCalendar CalendarToXmlGregorianCalendarConverter(Calendar s){
		if( GregorianCalendar.class.isAssignableFrom(s.getClass()) ){
			return getDatatypeFactory().newXMLGregorianCalendar( (GregorianCalendar)s );
		}
		
		GregorianCalendar cal = new GregorianCalendar();
		cal.setTime(s.getTime());
	      	cal.setTimeZone(s.getTimeZone());
		
		XMLGregorianCalendar gregorianCalendar = createInstance();
		return gregorianCalendar;
    }
    
    public static XMLGregorianCalendar DateToXmlGregorianCalendarConverter(Date s){
		GregorianCalendar calendar = new GregorianCalendar();
		calendar.setTime(s);
		return getDatatypeFactory().newXMLGregorianCalendar(calendar);
    }
    
    public static XMLGregorianCalendar LongToXmlGregorianCalendarConverter(Long s){
		GregorianCalendar calendar = new GregorianCalendar();
		calendar.setTimeInMillis(s);
		return getDatatypeFactory().newXMLGregorianCalendar(calendar);
    }

    public static java.sql.Date XmlGregorianCalendarToSqlDateConverter(XMLGregorianCalendar s){
    	return s == null ? null : DefaultConverters.toSqlDate(s.toGregorianCalendar());
    }

    public static Time XmlGregorianCalendarToTimeConverter(XMLGregorianCalendar s){
    	return s == null ? null : DefaultConverters.toTime(s.toGregorianCalendar());
    }

    public static Timestamp XmlGregorianCalendarToTimestampConverter(XMLGregorianCalendar s){
    	return s == null ? null : new Timestamp(s.toGregorianCalendar().getTimeInMillis());
    }
	
}
