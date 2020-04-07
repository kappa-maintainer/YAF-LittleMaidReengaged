package net.blacklab.lmr.util.manager;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import net.blacklab.lmr.LittleMaidReengaged;
import net.blacklab.lmr.entity.maidmodel.TextureBox;
import net.blacklab.lmr.util.EnumSound;
import net.blacklab.lmr.util.loader.LMSoundHandler;
import net.blacklab.lmr.util.loader.config.JsonConfigLittleMaidCustomSound;
import net.blacklab.lmr.util.loader.config.JsonConfigLittleMaidSound;
import net.blacklab.lmr.util.loader.config.JsonConfigLittleMaidCustomSound.ModelVoice;
import net.minecraft.util.ResourceLocation;

/**
 * メイドさんのサウンド関連を管理する
 * @author firis-games
 *
 */
public class SoundManager {
	
	public static SoundManager instance = new SoundManager();
	
	/**
	 * カスタムモデルサウンド
	 * 
	 * モデル＋カラー番号でボイス設定を切り替える
	 */
	private JsonConfigLittleMaidCustomSound customSound = null;
	
	/**
	 * デフォルトのサウンドパックを設定する
	 */
	private String defaultSoundpack = "";
	
	/**
	 * Sounds.jsonで利用しているoggファイルのClassloaderパスを保持する
	 */
	private List<String> classloaderResoucePath = new ArrayList<>();
	
	/**
	 * 読み込んだSoundから必要なものを生成する
	 * モデルの生成処理が終わっていることが前提
	 */
	public void createSounds() {
		
		//サウンドパックがロードされていない場合は何もしない
		if (LMSoundHandler.jsonSoundList.size() == 0) return;
		
		//デフォルトボイス設定追加
		this.defaultSoundpack = LMSoundHandler.jsonSoundList.get(0).voiceName;
		
		//カスタムのJson生成
		if (!this.loadJsonCustomModelVoice()) {
			//読み込めなかった場合に作成
			this.createJsonCustomModelVoice();
		}
		
		//sounds.json生成
		this.createJsonMinecraftSounds();
		
		//セットアップ
		//Mod内で使用する形式へ変換する
		for (JsonConfigLittleMaidSound soundinfo : LMSoundHandler.jsonSoundList) {
			
			//変換処理
			for(String voiceId : soundinfo.voices.keySet()) {
				
				//クラスローダーのパスをセットする
				for (String voicePath : soundinfo.voices.get(voiceId)) {
					classloaderResoucePath.add(voicePath);
				}
				
			}
		}
	}
	
	/**
	 * カスタム設定を読込
	 */
	private boolean loadJsonCustomModelVoice() {
		
		boolean ret = false;
		try {
			//rootフォルダのチェック
			Path config = Paths.get("mods", "LittleMaidReengaged", "custom_model_sounds.json");
			
			if (Files.notExists(config)) {
				return false;
			}
			
			//テキストとして読込
			List<String> fileList = Files.readAllLines(config);
			String json = String.join("", fileList);
			
			//Jsonへパース
			Gson gson = new Gson();
			customSound = gson.fromJson(json, JsonConfigLittleMaidCustomSound.class);
			
			//処理成功
			ret = true;
			
		} catch (Exception e) {
			e.printStackTrace();
		}
		return ret;
	}

	/**
	 * モデルとサウンドパックを紐づける設定を生成する
	 */
	private void createJsonCustomModelVoice() {
		
		JsonConfigLittleMaidCustomSound jsonObject = new JsonConfigLittleMaidCustomSound();
		jsonObject.default_voice = this.defaultSoundpack;
		
		for (TextureBox textureBox : ModelManager.getTextureList()) {
			
			//デフォルトテクスチャが存在する場合のみ対象とする
			if (textureBox.getTextureNameDefault() != null) {
				
				JsonConfigLittleMaidCustomSound.ModelVoice modelVoice = new JsonConfigLittleMaidCustomSound.ModelVoice(jsonObject.default_voice);
				
				//カラー情報を持つ場合のみ個別カラー設定を追加する
				for (int color = 0; color < 16; color++) {
					if (textureBox.hasColor(color)) {
						modelVoice.addColor(color);
					}
				}
				//カスタマイズ情報を書き出し
				jsonObject.custom_voice.put(textureBox.textureName, modelVoice);
			}			
		}
		
		String jsonStr = new GsonBuilder()
				.serializeNulls()
				.setPrettyPrinting()
				.disableHtmlEscaping()
				.create().toJson(jsonObject);
		
		this.wirteToFile("custom_model_sounds.json", jsonStr);
		
		//生成したものを保持する
		customSound = jsonObject;
		
	}
	
	/**
	 * サウンドパック一覧を元にMinecraftで使用する
	 * @return
	 */
	private void createJsonMinecraftSounds() {
		
		JsonObject jsonObject = new JsonObject();
		
		//サウンドパック一覧をMinecraftのSounds.json形式へ変換する
		for (JsonConfigLittleMaidSound record : LMSoundHandler.jsonSoundList) {
			
			//レコード単位で生成する
			for (String voiceId : record.voices.keySet()) {
				
				JsonObject elementObject = new JsonObject();
				
				//音声名
				String elementName = record.voiceName + "." + voiceId;
				
				//category
				elementObject.addProperty("category", "master");
				
				//Sounds
				JsonArray soundsElements = new JsonArray();
				for (String voice : record.voices.get(voiceId)) {
					//sounds.json形式のパスへ変換する
					String voicePath = voice;
					voicePath = LittleMaidReengaged.DOMAIN + ":" + elementName + "//" + voicePath;
					soundsElements.add(voicePath);
				}
				elementObject.add("sounds", soundsElements);
				
				//親Objectへ挿入する
				jsonObject.add(elementName, elementObject);
			}
		}
		
		String jsonStr = new GsonBuilder()
				.serializeNulls()
				.setPrettyPrinting()
				.disableHtmlEscaping()
				.create().toJson(jsonObject);
		
		this.wirteToFile("lm_sounds.json", jsonStr);
	}
	
	/**
	 * ファイルを書き出す
	 * @param name
	 * @param json
	 */
	private void wirteToFile(String fileName, String json) {
		
		//書き出す
		//出力
		List<String> jsonList = new ArrayList<>();
		jsonList.add(json);
		
		//Path
		Path dict = Paths.get("mods", "LittleMaidReengaged");
		Path path = Paths.get("mods", "LittleMaidReengaged", fileName);
		
		try {
			if (!Files.isDirectory(dict)) {
				Files.createDirectories(dict);
			}
			Files.write(path, jsonList, Charset.forName("UTF-8"), StandardOpenOption.CREATE_NEW);
		} catch (IOException e) {
		}
	}
	
	/**
	 * 対象の音声が存在するか判断する
	 * @param resoucePath
	 * @return
	 */
	public boolean isResourceExists(ResourceLocation resource) {
		//音声パスが存在するかチェックする
		return !getConvertSoundPath(resource).equals("");
	}
	
	/**
	 * 音声の実体パスを返却する
	 * @param resource
	 * @return
	 */
	public String getResourceClassLoaderPath(ResourceLocation resource) {
		return getConvertSoundPath(resource);
	}
	
	/**
	 * ResourceLocationから音声パスの実体を取得する
	 * Minecraft1.12.2ではパスは強制的に小文字になるためここで変換する
	 * @param resource
	 * @return
	 */
	private String getConvertSoundPath(ResourceLocation resource) {
		
		String ret = "";
		
		String path = "";
		String[] paths = resource.getResourcePath().split("//");
		if (paths.length == 2) {
			path = paths[1].replace(".ogg", "") + ".ogg";
		}
		
		for (String classLoaderPath : classloaderResoucePath) {
			//小文字化して照合する
			if (path.toLowerCase().equals(classLoaderPath.toLowerCase())) {
				ret = classLoaderPath;
				break;
			}
		}
		return ret;
	}
	
	/**
	 * デフォルトボイスパックがない場合はサウンドパックがないと判断する
	 * @return
	 */
	public boolean isFoundSoundpack() {
		return !defaultSoundpack.equals("");
	}
	
	/**
	 * モデルとカラー情報をもとに音声を取得する
	 * @param sound
	 * @param texture
	 * @param color
	 * @return
	 */
	public String getSoundNameWithModel(EnumSound sound, String texture, Integer color) {
		
		String soundpack = this.defaultSoundpack;
		
		//カスタムサウンド設定から使用するSoundpackを取得する
		for (String modelName : customSound.custom_voice.keySet()) {
			if (modelName.equals(texture)) {
				ModelVoice modelVoice = customSound.custom_voice.get(modelName);
				//モデルのデフォルトパックを反映
				soundpack = modelVoice.default_voice;
				//色情報を確認
				if (modelVoice.color_voice != null &&
						modelVoice.color_voice.containsKey(color)) {
					//色の個別設定があれば参照する
					soundpack = modelVoice.color_voice.get(color);
				}
				break;
			}
		}
		
		//サウンドIDを生成
		String soundType = soundpack + "." + sound.toString();
		return soundType;
	}
}