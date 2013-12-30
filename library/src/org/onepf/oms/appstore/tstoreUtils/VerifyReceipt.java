/*******************************************************************************
 * Copyright 2013 One Platform Foundation
 *
 *       Licensed under the Apache License, Version 2.0 (the "License");
 *       you may not use this file except in compliance with the License.
 *       You may obtain a copy of the License at
 *
 *           http://www.apache.org/licenses/LICENSE-2.0
 *
 *       Unless required by applicable law or agreed to in writing, software
 *       distributed under the License is distributed on an "AS IS" BASIS,
 *       WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *       See the License for the specific language governing permissions and
 *       limitations under the License.
 ******************************************************************************/

package org.onepf.oms.appstore.tstoreUtils;

import java.util.List;

/**
 * Author: Ruslan Sayfutdinov
 * Date: 05.04.13
 */
public class VerifyReceipt {
    public int status;
    public String detail;
    public String message;
    public int count;
    public List<Product> product;

    public static class Product {
        public String log_time;
        public String appid;
        public String product_id;
        public double charge_amount;
        public String tid;
        public String detail_pname;
        public String bp_info;
        public String tcash_flag;

        @Override
        public String toString() {
            return ("log_time:" + log_time + "\n") + "appid:" + appid + "\n" + "product_id:" + product_id + "\n" + "charge_amount:" + charge_amount + "\n" + "tid:" + tid + "\n" + "detail_pname:" + detail_pname + "\n" + "bp_info:" + bp_info + "\n" + "tcash_flag:" + tcash_flag + "\n";
        }

    }

    @Override
    public String toString() {
        StringBuilder b = new StringBuilder("[VerifyReceipt]\n");
        b.append("status:").append(status).append("\n").append("detail:").append(detail).append("\n").append("message:").append(message).append("\n").append("count:").append(count).append("\n");
        if (this.product != null) {
            for (Product p : this.product) {
                b.append("{product}\n").append(p.toString()).append("\n");
            }
        }
        return b.toString();
    }

}

