package com.example.hrapp.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.gotrue.Auth

object SupabaseProvider {
    val client: SupabaseClient by lazy {
        createSupabaseClient(
            supabaseUrl = "https://rjmcivhnmxcziyocmndv.supabase.co/rest/v1/",
            supabaseKey = "sb_publishable_6iS-fp2-Mys24uPppBC4Kw_bv7QcRyZ"
        ) {
            install(Postgrest)
            install(Auth)
        }
    }
}
