package com.astralbody888.alexanderconner.newsreaderapp;

import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;
import android.os.AsyncTask;
import android.support.v4.content.ContextCompat;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {

    ListView listView;
    SwipeRefreshLayout mSwipeRefreshLayout;
    ArrayList<String> titles = new ArrayList<>();
    ArrayList<String> urlList = new ArrayList<>();
    ArrayAdapter arrayAdapter;

    SQLiteDatabase articlesDB;

    //Set how many items are displayed
    int numberOfItems = 25;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mSwipeRefreshLayout = (SwipeRefreshLayout) findViewById(R.id.activity_main_swipe_refresh_layout);
        listView = (ListView) findViewById(R.id.newsListView);
        arrayAdapter = new ArrayAdapter(this, android.R.layout.simple_list_item_1, titles);

        listView.setAdapter(arrayAdapter);
        listView.setOnItemClickListener(onTitleClick);
        mSwipeRefreshLayout.setOnRefreshListener(refreshListener);
        mSwipeRefreshLayout.setColorSchemeColors(ContextCompat.getColor(this, R.color.orange),ContextCompat.getColor(this, R.color.green),ContextCompat.getColor(this, R.color.blue));

        articlesDB = this.openOrCreateDatabase("Articles", MODE_PRIVATE, null);

        articlesDB.execSQL("CREATE TABLE IF NOT EXISTS articlesTable (id INTEGER PRIMARY KEY, articleID INTEGER, title VARCHAR, url VARCHAR)");

        refreshArticleFeed();
    }

    private void refreshArticleFeed() {
        DownloadTask task = new DownloadTask();
        try {
            task.execute("https://hacker-news.firebaseio.com/v0/topstories.json?print=pretty");
        } catch(Exception e) {
            Toast.makeText(this, "Cannot load Hacker News site...", Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
    }

    AdapterView.OnItemClickListener onTitleClick = new AdapterView.OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
            Intent intent = new Intent(getApplicationContext(), ArticleActivity.class);
            intent.putExtra("url", urlList.get(i));

            startActivity(intent);
        }
    };

    SwipeRefreshLayout.OnRefreshListener refreshListener = new SwipeRefreshLayout.OnRefreshListener() {
        @Override
        public void onRefresh() {
            refreshArticleFeed();


        }
    };

    public void updateListView() {
        Cursor c = articlesDB.rawQuery("SELECT * FROM articlesTable", null);

        int urlIndex = c.getColumnIndex("url");
        int titleIndex = c.getColumnIndex("title");

        if (c.moveToFirst()) {
            Log.i("Update Titles", "Clearing titles list");
            titles.clear();
            urlList.clear();

            do {
                titles.add(c.getString(titleIndex));
                urlList.add(c.getString(urlIndex));
            } while (c.moveToNext()); {
                arrayAdapter.notifyDataSetChanged();
            }
        }
        mSwipeRefreshLayout.setRefreshing(false);

    }

    public class DownloadTask extends AsyncTask<String, Void, String> {

        @Override
        protected String doInBackground(String... strings) {

            String result = "";
            URL url;
            HttpURLConnection urlConnection = null;

            try {
                //Get all the Article IDs from today..
                url = new URL(strings[0]);

                urlConnection = (HttpURLConnection) url.openConnection();

                InputStream in = urlConnection.getInputStream();

                InputStreamReader reader = new InputStreamReader(in);

                int data = reader.read();

                while (data != -1) {
                    char current = (char) data;

                    result += current;

                    data = reader.read();
                }

                Log.i("URLContent", result);

                JSONArray jsonArray = new JSONArray(result);

                if (jsonArray.length() < numberOfItems) {
                    numberOfItems = jsonArray.length();
                }

                //Clear the table each time the app is loaded...
                articlesDB.execSQL("DELETE FROM articlesTable");

                //Get the data for each Article ID...
                for (int i = 0; i < numberOfItems; i++) {
                    //Log.i("JSONItem", jsonArray.getString(i));
                    String articleID = jsonArray.getString(i);

                    url = new URL("https://hacker-news.firebaseio.com/v0/item/" + articleID + ".json?print=pretty");
                    urlConnection = (HttpURLConnection) url.openConnection();

                    in = urlConnection.getInputStream();
                    reader = new InputStreamReader(in);
                    data = reader.read();

                    String articleInfo = "";

                    while (data != -1) {
                        char current = (char) data;

                        articleInfo += current;
                        data = reader.read();
                    }

                    //Log.i("ArticleID", articleInfo);

                    JSONObject articleJSON = new JSONObject(articleInfo);

                    if (!articleJSON.isNull("title") && !articleJSON.isNull("url")) {
                        String articleTitle = articleJSON.getString("title");
                        String articleURL = articleJSON.getString("url");

                        //Log.i("Article info", articleTitle + articleURL);

                        String sql = "INSERT INTO articlesTable (articleID, title, url) VALUES (?, ?, ?)";
                        SQLiteStatement statement = articlesDB.compileStatement(sql);
                        statement.bindString(1, articleID);
                        statement.bindString(2, articleTitle);
                        statement.bindString(3, articleURL);

                        statement.execute();
                    }
                }

            } catch (MalformedURLException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (JSONException e) {
                e.printStackTrace();
            }

            return null;
        }

        @Override
        protected void onPostExecute(String s) {
            super.onPostExecute(s);
            Log.i("Update", "Post execution Updating list view...");
            updateListView();
        }
    }
}
