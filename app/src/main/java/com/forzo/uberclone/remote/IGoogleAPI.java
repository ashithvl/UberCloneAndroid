package com.forzo.uberclone.remote;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Url;

/**
 * Created by Leon on 22-03-18.
 */

public interface IGoogleAPI {

    @GET
    Call<String> getPath(@Url String url);
}
