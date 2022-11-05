package com.tsid.api.util;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.StringTokenizer;

public class DanalUtil {


    public static Map str2data(String str) {

        Map map = new HashMap();
        StringTokenizer st = new StringTokenizer(str,"&");

        while(st.hasMoreTokens()){

            String pair = st.nextToken();
            int index = pair.indexOf('=');

            if(index > 0){
                map.put(pair.substring(0,index).trim(), pair.substring(index+1));
            }
        }

        return map;
    }

    public String data2str(Map data) {

        Iterator i = data.keySet().iterator();
        StringBuffer sb = new StringBuffer();

        while(i.hasNext()){
            Object key = i.next();
            Object value = data.get(key);
            sb.append(key.toString()+"="+value+"&");
        }

        if(sb.length()>0) {
            return sb.substring(0,sb.length()-1);
        } else {
            return "";
        }
    }

    public static String MakeFormInputHTTP(Map HTTPVAR,String arr) {

        StringBuffer ret = new StringBuffer();
        String key = "";
        String[] value = null;

        Iterator i = HTTPVAR.keySet().iterator();

        while( i.hasNext() )
        {
            key = (String)i.next();

            if( key.equals(arr) )
            {
                continue;
            }

            value = (String[])HTTPVAR.get(key);

            for( int j=0;j<value.length;j++ )
            {
                ret.append( "<input type=\"hidden\" name=\"" );
                ret.append( key );
                ret.append( "\" value=\"" );
                ret.append( value[j] );
                ret.append( "\">" );
                ret.append( "\n" );
            }
        }

        return ret.toString();
    }

    public static String MakeFormInput(Map map,String[] arr) {

        StringBuffer ret = new StringBuffer();

        if( arr!=null )
        {
            for( int i=0;i<arr.length;i++ )
            {
                if( map.containsKey(arr[i]) )
                {
                    map.remove( arr[i] );
                }
            }
        }

        Iterator i = map.keySet().iterator();

        while( i.hasNext() )
        {
            String key = (String)i.next();
            String value = (String)map.get(key);

            ret.append( "<input type=\"hidden\" name=\"" );
            ret.append( key );
            ret.append( "\" value=\"" );
            ret.append( value );
            ret.append( "\">" );
            ret.append( "\n" );
        }

        return ret.toString();
    }

    public String GetBgColor(String BgColor) {

        /*
         * Default : Blue
         */
        int Color = 0;
        int nBgColor = Integer.parseInt(BgColor);

        if( nBgColor > 0 && nBgColor < 11 )
        {
            Color = nBgColor;
        }

        if( Color >= 0 && Color <= 9 )
        {
            return "0" + Integer.toString(Color);
        }
        else
        {
            return Integer.toString(Color);
        }
    }
}
