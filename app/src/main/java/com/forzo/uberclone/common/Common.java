package com.forzo.uberclone.common;

import com.forzo.uberclone.remote.IGoogleAPI;
import com.forzo.uberclone.remote.RetrofitClient;

import retrofit2.Retrofit;

/**
 * Created by Leon on 22-03-18.
 */

public class Common {
    public static final String baseURL = "https://maps.googleapis.com";

    public static IGoogleAPI getIGoogleAPI() {
        return RetrofitClient.getClient(baseURL).create(IGoogleAPI.class);
    }
}
