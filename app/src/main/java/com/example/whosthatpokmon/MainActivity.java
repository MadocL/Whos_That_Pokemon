package com.example.whosthatpokmon;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;

import android.os.Build;
import android.os.Bundle;
import android.os.StrictMode;
import android.util.JsonReader;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import net.ricecode.similarity.JaroWinklerStrategy;
import net.ricecode.similarity.SimilarityStrategy;
import net.ricecode.similarity.StringSimilarityService;
import net.ricecode.similarity.StringSimilarityServiceImpl;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Random;

public class MainActivity extends AppCompatActivity{
    private static final int NB_POKEMON = 898;

    int combo = 0;
    String name;
    Random random = new Random();

    //pour detecter les string similaires
    SimilarityStrategy strategy = new JaroWinklerStrategy();
    StringSimilarityService service = new StringSimilarityServiceImpl(strategy);

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button btnGuess = findViewById(R.id.btnGuess);
        Button btnSkip = findViewById(R.id.btnSkip);
        EditText editText = findViewById(R.id.editText);

        btnSkip.setBackgroundColor(Color.rgb(255, 123, 0));
        btnGuess.setBackgroundColor(Color.rgb(255, 123, 0));
        editText.setBackgroundColor(Color.WHITE);
        editText.setTextColor(Color.BLACK);
        reloadCombo();

        try {
            newGame();
        } catch (IOException e) {
            e.printStackTrace();
        }
        btnGuess.setOnClickListener(view -> {
            //la saisie est bonne
            if (normalizeText(editText.getText().toString()).equalsIgnoreCase(normalizeText(name))) {
                editText.getText().clear();
                Toast.makeText(getApplicationContext(),
                        "Bien ouej t'as trouvé",
                        Toast.LENGTH_SHORT
                ).show();
                combo++;
                reloadCombo();

                try {
                    newGame();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            //la saisie est proche
            else if(service.score(normalizeText(editText.getText().toString()), normalizeText(name)) >= 0.90){
                Toast.makeText(
                        getApplicationContext(),
                        "Oh mec t'es pas loin",
                        Toast.LENGTH_SHORT
                ).show();
            }
            //mauvaise saisie
            else {
                Toast.makeText(getApplicationContext(), "Nop", Toast.LENGTH_SHORT).show();
                combo = 0;
                reloadCombo();
                editText.getText().clear();
            }
        });

        btnSkip.setOnClickListener(view -> {
            try {
                newGame();
            } catch (IOException e) {
                e.printStackTrace();
            }
            combo = 0;
            reloadCombo();
            editText.getText().clear();
        });
    }

    public void reloadCombo(){
        ((TextView)findViewById(R.id.combo)).setText("Combo : " + combo);
    }

    //setup la nouvelle partie
    @RequiresApi(api = Build.VERSION_CODES.O)
    public void newGame() throws IOException {
        int randomPkm = random.nextInt(NB_POKEMON) + 1; //numero du pokemon a trouver
        name = getNameFromAPI(randomPkm);
        Bitmap artwork = getArtworkFromAPI(randomPkm);
        ((ImageView)findViewById(R.id.imageView)).setImageBitmap(getSilhouette(artwork));
    }

    //normalise la string en caractere ascii (suppression des eventuels accents/tremas)
    public static String normalizeText(String src){
        return Normalizer.normalize(src, Normalizer.Form.NFD).replaceAll("[^\\p{ASCII}]", "");
    }

    //recupere par requete http le nom du pokemon lie au numero donne
    public static String getNameFromAPI(int randomPkm) throws IOException {
        URL url = new URL("https://pokeapi.co/api/v2/pokemon-species/" + randomPkm);
        String name;

        HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
        try {
            InputStream in = new BufferedInputStream(urlConnection.getInputStream());
            name = readJsonStream(in);
        } finally {
            urlConnection.disconnect();
        }
        return name;
    }

    //parser pour le fichier json de l'api PokeApi (ne recupere que le nom francais)
    public static String readJsonStream(InputStream in) throws IOException {
        JsonReader reader = new JsonReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        String name = null;
        try {
            reader.beginObject();
            while (reader.hasNext()) {
                if(reader.nextName().equals("names")) {
                    reader.beginArray();
                    while (reader.hasNext()){
                        reader.beginObject();
                        reader.nextName();
                        reader.beginObject();
                        reader.nextName();
                        if(reader.nextString().equals("fr")){
                            reader.nextName();
                            reader.skipValue();
                            reader.endObject();
                            reader.nextName();
                            name = reader.nextString();
                            break;
                        }
                        reader.nextName();
                        reader.skipValue();
                        reader.endObject();
                        reader.nextName();
                        reader.skipValue();
                        reader.endObject();
                    }
                    break;
                }
                reader.skipValue();
            }
            reader.endObject();
        } finally {
            reader.close();
        }
        return name;
    }

    //recupere l'artwork officiel du pokemon de numero donne
    public static Bitmap getArtworkFromAPI(int randomPkm) throws IOException {
        URL url = new URL("https://raw.githubusercontent.com/PokeAPI/" +
                "sprites/master/sprites/pokemon/other/official-artwork/" +
                randomPkm + ".png");
        Bitmap artwork;
        HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
        try {
            InputStream in = new BufferedInputStream(urlConnection.getInputStream());
            artwork = BitmapFactory.decodeStream(in);
        } finally {
            urlConnection.disconnect();
        }
        return artwork;
    }

    //retourne une nouvelle image silhouette a partir de l'artwork donne
    //si le pixel est transparent (valeur alpha) alors mmetre a blanc, s'il est opaque alors mettre a noir
    @RequiresApi(api = Build.VERSION_CODES.O)
    public static Bitmap getSilhouette(Bitmap artwork){
        Bitmap silhouette;
        int pixelsBound = artwork.getWidth()* artwork.getHeight(); //nombre de pixels de l'artwork
        int[] pixels = new int[pixelsBound];
        artwork.getPixels(pixels, 0, artwork.getWidth(), 0, 0, artwork.getWidth(), artwork.getHeight());

        for(int i = 0; i < pixelsBound; i++)
        {
            if(Color.alpha(pixels[i]) == 0)
                pixels[i] = Color.rgb(255, 255, 255);
            else
                pixels[i] = Color.rgb(0, 0, 0);
        }
        silhouette = Bitmap.createBitmap(
                pixels,
                0,
                artwork.getWidth(),
                artwork.getWidth(),
                artwork.getHeight(),
                artwork.getConfig()
        );
        return silhouette;
    }
}