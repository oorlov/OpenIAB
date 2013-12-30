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
public class Response {
    public final String api_version;
    public final String identifier;
    public final String method;
    public final Result result;

    public Response(final String version, final String id, final String method,
                    final Result r) {
        this.api_version = version;
        this.identifier = id;
        this.method = method;
        this.result = r;
    }

    public static class Result {
        public final String message;
        public final String code;
        public String txid;
        public String receipt;
        public final int count;

        public final List<Product> product;

        public Result(final String code, final String message, final int count,
                      final List<Product> product) {
            this.message = message;
            this.code = code;
            this.count = count;
            this.product = product;
        }

        public Result(final String code, final String message, final String txid, final String receipt, final int count,
                      final List<Product> product) {
            this.message = message;
            this.code = code;
            this.count = count;
            this.product = product;
            this.txid = txid;
            this.receipt = receipt;
        }

        public String toString() {
            StringBuilder b = new StringBuilder();
            b.append("message:").append(this.message).append("\n").append("code:").append(this.code).append("\n").append("count:").append(this.count).append("\n");
            if (this.product != null) {
                for (Product p : this.product) {
                    b.append("{product}\n").append(p.toString()).append("\n");
                }
            }
            return b.toString();
        }
    }

    public static class Product {
        public String appid;
        public String id;
        public String name;
        public String type;
        public String kind;
        public int validity;
        public double price;
        public String startDate;
        public String endDate;
        public boolean purchasability;
        public Status status;

        @Override
        public String toString() {
            StringBuilder b = new StringBuilder();
            b.append("id:").append(id).append("\n").append("name:").append(name).append("\n").append("appid:").append(appid).append("\n").append("type:").append(type).append("\n").append("kind:").append(kind).append("\n").append("validity:").append(validity).append("\n").append("price:").append(price).append("\n").append("startDate:").append(startDate).append("\n").append("endDate:").append(endDate).append("\n").append("purchasability:").append(purchasability).append("\n");
            if (status != null) {
                b.append("{status}\n").append(status.toString()).append("\n");
            }
            return b.toString();
        }
    }

    public static class Status {
        public String code;
        public String message;

        public Status(final String code, final String msg) {
            this.code = code;
            this.message = msg;
        }

        @Override
        public String toString() {
            return "code:" + code + "\n" + "message:" + message + "\n";
        }
    }

    @Override
    public String toString() {
        return "[Response]\n" + "api_version:" + api_version + "\n" + "identifier:" + identifier + "\n" + "method:" + method + "\n" + "{result}\n" + result.toString();
    }

}
