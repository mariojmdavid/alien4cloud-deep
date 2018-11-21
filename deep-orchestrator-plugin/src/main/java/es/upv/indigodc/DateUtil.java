package es.upv.indigodc;

import java.util.Date;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Class taken from the alien4cloud-cloudify4-provider plugin source code
 * @author alien4cloud-cloudify4-provider team
 *
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class DateUtil {

    public static int compare(Date left, Date right) {
        if (left == null) {
            if (right != null) {
                return -1;
            } else {
                return 0;
            }
        } else {
            if (right != null) {
                return left.compareTo(right);
            } else {
                return 1;
            }
        }
    }
}
