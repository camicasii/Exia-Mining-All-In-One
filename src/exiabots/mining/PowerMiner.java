package exiabots.mining;

import java.awt.Color;
import java.awt.Point;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

import com.runemate.game.api.hybrid.Environment;
import com.runemate.game.api.hybrid.entities.GameObject;
import com.runemate.game.api.hybrid.entities.LocatableEntity;
import com.runemate.game.api.hybrid.entities.Player;
import com.runemate.game.api.hybrid.entities.definitions.GameObjectDefinition;
import com.runemate.game.api.hybrid.entities.definitions.ItemDefinition;
import com.runemate.game.api.hybrid.input.Mouse;
import com.runemate.game.api.hybrid.local.Camera;
import com.runemate.game.api.hybrid.local.Screen;
import com.runemate.game.api.hybrid.local.hud.InteractablePoint;
import com.runemate.game.api.hybrid.local.hud.InteractableRectangle;
import com.runemate.game.api.hybrid.local.hud.Menu;
import com.runemate.game.api.hybrid.local.hud.MenuItem;
import com.runemate.game.api.hybrid.local.hud.interfaces.InterfaceWindows;
import com.runemate.game.api.hybrid.local.hud.interfaces.Inventory;
import com.runemate.game.api.hybrid.local.hud.interfaces.SpriteItem;
import com.runemate.game.api.hybrid.location.Coordinate;
import com.runemate.game.api.hybrid.player_sense.PlayerSense;
import com.runemate.game.api.hybrid.queries.results.LocatableEntityQueryResults;
import com.runemate.game.api.hybrid.queries.results.SpriteItemQueryResults;
import com.runemate.game.api.hybrid.region.GameObjects;
import com.runemate.game.api.hybrid.region.Players;
import com.runemate.game.api.hybrid.util.Timer;
import com.runemate.game.api.hybrid.util.calculations.Random;
import com.runemate.game.api.rs3.local.InterfaceMode;
import com.runemate.game.api.rs3.local.hud.interfaces.eoc.ActionBar;
import com.runemate.game.api.rs3.local.hud.interfaces.eoc.ActionBar.Slot;
import com.runemate.game.api.rs3.local.hud.interfaces.eoc.ActionWindow;
import com.runemate.game.api.rs3.local.hud.interfaces.legacy.LegacyTab;
import com.runemate.game.api.script.Execution;

import exiabots.ExiaMinerAIO;
import exiabots.mining.RockWatcher.Pair;
import exiabots.mining.RockWatcher.Validater;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;

public class PowerMiner extends MiningStyle{

	private boolean dropping = false;
	private boolean powerDrop = false;
	private boolean mine1drop1 = false;
	private boolean forceKeys = false;
	private boolean actionBar = false;
	private boolean closeInv = false;
	private boolean ignoreItems = false;
	private boolean usePorters = false;
	private boolean useUrns = false;
	private boolean useJujus = false;
	private boolean dropGems = true;
	private Coordinate playerStart;

	int dropOffset = 0;
	double radius = 10;
	Coordinate center = null;
	int notMiningCount = 0;
	private Rock ore;
	boolean[] dropped = new boolean[28];

	boolean first = true;
	
	@Override
	public void onStart(String... args) {
		
		if(ExiaMinerAIO.isRS3){
			rockWatcher = new RockWatcher(new Validater(){
				@Override
				public boolean validate(GameObject o) {
					GameObjectDefinition def = o.getDefinition();
					Map<Color, Color> colors = new HashMap<Color, Color>();
					String name = "";
					int id = 0;

					if(def != null){
						id = o.getId();
						name = def.getName();
						colors = def.getColorSubstitutions();
					}

					if(ore.name.equals("Granite")){
						return id != 2560 && name.contains("rocks");
					}else if(ore.name.equals("SandStone")){
						return id != 2551 && name.contains("rocks");
					}else if(ore.name.equals("Seren")){
						return name.contains("Seren stone");
					}else if(ore.name.equals("Concentrated Coal")){
						return name.contains("Mineral") && colors.containsValue(new Color(5,6,5));	
					}else if(ore.name.equals("Concentrated Gold")){
						return name.contains("Mineral") && colors.size() == 0;
					}else{
						return validateRS3(o);
					}
				}

			}, new Coordinate[]{});
		}else{
			rockWatcher = new RockWatcher((GameObject rock) -> validateOSRS(rock), new Coordinate[]{});
		}

		Paint.profitCounter.setLocked(true);
		ExiaMinerAIO.instance.getEventDispatcher().addListener(Paint.profitCounter);

		rockWatcher.start();
		content = null;
	}

	@Override
	public void onStop() {
		if(rockWatcher != null){
			rockWatcher.interrupt();
		}
	}

	@Override
	public String getLocationName() {
		return "Power Mining";
	}

	@Override
	public Coordinate[] getRockLocations(){
		return rockWatcher.getLocations();
	}

	@Override
	public Rock getOre() {
		return ore;
	}

	@Override
	public void loop() {
		Player player;
		if(first && (player = Players.getLocal()) != null){
			playerStart = player.getPosition();
			first = false;
		}
		
		if(useUrns){
			ItemHandlers.manageUrns(0);
		}

		if(usePorters){
			ItemHandlers.managePorters();
		}	

		if(useJujus){
			ItemHandlers.manageJujus();
		}

		if(shouldDrop()){
			dropping = true;
			drop();
		}else{
			dropOffset = 0;
			mine();
			if(Players.getLocal().getAnimationId() == -1)notMiningCount++;
			else notMiningCount = 0;

			if(notMiningCount >= 9){
				notMiningCount = 0;
				currentRock = null;
			}
		}
	}

	private void mine() {
		//reset some of the dropping variables
		dropped = new boolean[28];
		ignoreItems = false;

		//If the inventory was not initially open, close it
		if(actionBar && closeInv && InterfaceWindows.getInventory().isOpen()){
			if(Environment.isRS3()){
				if(InterfaceMode.getCurrent() == InterfaceMode.LEGACY){
					LegacyTab.BACKPACK.close();
				}else{
					ActionWindow.BACKPACK.close();
				}
				ReflexAgent.delay();
				return;
			}
		}

		if(currentRock == null || !currentRock.isValid()){
			currentRock = null;

			//Get a new rock
			LocatableEntity rock = getNextRock();
			if(rock != null){
				rockWatcher.addLocation(rock.getPosition());
				Player me = Players.getLocal();
				if(rock.distanceTo(me) > 16){
					Paint.status = "Walking to rock";
					walkTo(rock);
				}else{
					turnAndClick(rock);
				}
			}else{
				Paint.status = "Preparing for respawn";
				//if there are no new rocks to get, walk to the next spawning rock
				walkToNextEmpty();
				Paint.status = "Waiting for respawn";
			}
		}else{
			Paint.status = "Mining";
			if(!Players.getLocal().isMoving() && currentRock != null && currentRock.getVisibility() < 80){
				Camera.concurrentlyTurnTo((Camera.getYaw() + Random.nextInt(0, 360)) % 360);
			}
			hoverNext();
		}
	}

	private void hoverNext(){
		if(!actionBar && mine1drop1){
			Execution.delay(ReflexAgent.getReactionTime()/2);
			SpriteItemQueryResults items = Inventory.getItems();
			boolean[] open = new boolean[28];
			for(SpriteItem i : items){
				open[i.getIndex()] = true;
			}
			int i = 0;
			for(; i < 28; i++){
				if(!open[i]){
					break;
				}
			}

			Execution.delay(ReflexAgent.getReactionTime() * 3);
			InteractableRectangle bounds = Inventory.getBoundsOf(i);
			if(bounds != null){
				bounds.hover();
				ReflexAgent.delay();
			}
			return;
		}

		LocatableEntity rock = getNextRock();
		if(rock == null){
			Pair<Coordinate, Long, GameObject> pair = rockWatcher.nextRock();
			rock = pair == null ? null : pair.object;
		}

		if(rock != null){
			if(!rock.contains(Mouse.getPosition())){
				ReflexAgent.delay();
				InteractablePoint pt = rock.getInteractionPoint(new Point(Random.nextInt(-2,3), Random.nextInt(-2,3)));
				ReflexAgent.delay();
				if(pt != null){
					Mouse.move(pt);
				}else{
					rock.hover();
				}
			}else{
				if(rock instanceof GameObject && ((GameObject) rock).getVisibility() < 80){
					Camera.concurrentlyTurnTo((Camera.getYaw() + Random.nextInt(0, 360)) % 360);
				}
				if(Random.nextInt(0,100) < 5){
					InteractablePoint pt = rock.getInteractionPoint(new Point(Random.nextInt(-2,3), Random.nextInt(-2,3)));
					ReflexAgent.delay();
					if(pt != null){
						Mouse.move(pt);
					}else{
						rock.hover();
					}
				}
			}
		}
	}

	private LocatableEntity getNextRock() {
		LocatableEntityQueryResults<GameObject> rocksObjs = null;
		rocksObjs = GameObjects.getLoaded(new Predicate<GameObject>(){
			@Override
			public boolean test(GameObject o) {
				GameObjectDefinition def = o.getDefinition();
				String name = "";
				if(def != null)name = def.getName();

				if(Environment.isRS3()) return !o.equals(currentRock) && name.contains(ore.name) && o.distanceTo(center) <= radius && RockWatcher.validater.validate(o);
				else return !o.equals(currentRock) && o.distanceTo(center) <= radius && RockWatcher.validater.validate(o);
			}
		}).sortByDistance();

		if(rocksObjs != null && rocksObjs.size() > 0) return rocksObjs.get(0);
		return null;
	}

	Predicate<SpriteItem> orePredicate = new Predicate<SpriteItem>(){
		@Override
		public boolean test(SpriteItem i) {
			ItemDefinition def = i.getDefinition();
			String name = "";
			if(def != null)name = def.getName();

			return oreStringPredicate.test(name);
		}
	};

	Predicate<String> oreStringPredicate = new Predicate<String>(){
		@Override
		public boolean test(String name) {
			if(name == null)return false;
			
			for(String s : ore.oreNames){
				if(name.contains(s))return true;
			}
			
			if(dropGems){
				for(String s : Rock.GEMS.oreNames){
					if(name.contains(s))return true;
				}
			}
			
			return false;
		}
	};

	private boolean shouldDrop() {
		SpriteItemQueryResults items = Inventory.getItems(orePredicate);

		return !items.isEmpty() && (mine1drop1 || (Inventory.isFull() || dropping)) && !ignoreItems;
	}

	private void drop() {
		currentRock = null;
		deselect();
		SpriteItemQueryResults items = Inventory.getItems(orePredicate);

		if(items.isEmpty()){
			//no need to keep dropping
			dropping = false;
			return;
		}

		Paint.status = "Dropping";

		if(actionBar){
			//check if the action bar contains the ore we need to drop
			for(Slot slot : ActionBar.getFilledSlots()){
				if(slot != null){
					if(slot.getName() != null && oreStringPredicate.test(slot.getName())){
						//drop each of that item
						if(slot.isActivatable()){
							if(forceKeys) 
								slot.activate(false);
							else{
								slot.activate();
							}

							//If this player spams, then make them click twice
							if(Random.nextInt(100) <= PlayerSense.getAsInteger(CustomPlayerSense.Key.ACTION_BAR_SPAM.playerSenseKey))
								if(forceKeys) 
									slot.activate(false);

							ReflexAgent.delay();
							return;
						}
					}
				}
			}
			Paint.status = "Setting up action bar";
			//at this point, we didn't find it, so drag it over
			if(InterfaceWindows.getInventory().isOpen()){
				//find the first open action slot
				for(Slot slot : ActionBar.getFilledSlots()){
					//This indicates that this slot is not an ore dropping slot, so it's ok to overwrite it
					if(slot == null || !oreStringPredicate.test(slot.getName())){
						Mouse.drag(items.first(), slot.getBounds());
						//Wait 2-4 seconds for the item to appear on the action bar
						Timer timer = new Timer(Random.nextInt(2000,4000));
						timer.start();
						while(timer.getRemainingTime() > 0 && !oreStringPredicate.test(slot == null ? null : slot.getName())){
							Execution.delay(10);
						}
						ReflexAgent.delay();
						break;
					}
				}
			}else{
				InterfaceWindows.getInventory().open();
				closeInv = true;
			}

		}else{
			//Find ore in inventory
			if(InterfaceWindows.getInventory().isOpen()){
				if(powerDrop){
					ReflexAgent.delay();
					for(SpriteItem item : items){
						deselect();
						MenuItem mItem = null;
						while(mItem == null && item != null){
							InteractablePoint interact = item.getInteractionPoint();
							if(interact != null && Screen.getBounds().contains(interact)){
								Mouse.getPathGenerator().hop(interact);
								Mouse.click(Mouse.Button.LEFT);
							}
							Mouse.click(Mouse.Button.RIGHT);
							Execution.delay(50,100);
							mItem = Menu.getItem("Drop");
						}
						InteractablePoint interact = mItem.getInteractionPoint();
						if(interact != null && Screen.getBounds().contains(interact)){
							Mouse.getPathGenerator().hop(interact);
							Mouse.click(Mouse.Button.LEFT);
						}
					}
				}else{
					int offset = 0;
					for(int i = items.get(0).getIndex(); i < 28; i++)if(dropped[i])offset++;
					if(offset >= items.size()){
						dropping = false;
						dropped = new boolean[28];
						return;
					}

					ReflexAgent.delay();
					if(items.get(offset).interact("Drop")){
						dropped[items.get(offset).getIndex()] = true;
						ignoreItems = mine1drop1 && items.size() == 1;
					}
				}
			}else{
				InterfaceWindows.getInventory().open();
				closeInv = true;
			}
		}
	}

	private void deselect() {
		SpriteItem selected = Inventory.getSelectedItem();
		if(selected != null)selected.click();
	}

	public boolean validateRS3(GameObject rock) {
		GameObjectDefinition def = rock.getDefinition();
		String name = "";
		if(def != null)name = def.getName();

		return !name.equals("Rocks") && name.contains("rocks") && rock.getAnimationId() > 0;
	}

	public boolean validateOSRS(GameObject o) {
		Map<Color, Color> colors = new HashMap<Color, Color>();
		GameObjectDefinition def = o.getDefinition();
		String name = "";
		if(def != null){
			name = def.getName();
			colors = def.getColorSubstitutions();
		}

		for (int i = 0; i < ore.colors.length; i++) {
			if(colors.containsValue(ore.colors[i]) && name.contains("Rock"))return true;
		}
		return false;
	}

	private GridPane content = null;

	CheckBox mineOne = new CheckBox("Mine one drop one");
	CheckBox hotkeys = new CheckBox("Use action bar");
	CheckBox forceNoClick = new CheckBox("Force keyboard for action bar");
	CheckBox power = new CheckBox("Power drop (disable antiban for dropping)");
	CheckBox urnBox = new CheckBox("Use urns");
	CheckBox porterBox= new CheckBox("Use porters");
	CheckBox jujuBox= new CheckBox("Use juju potions");
	CheckBox gemBox= new CheckBox("Drop gems");
	CheckBox radLabel = new CheckBox("Radius:");
	TextField radText = new TextField("10");

	@Override
	public void loadSettings() {
		mine1drop1 = mineOne.isSelected();
		actionBar  = hotkeys.isSelected();
		powerDrop  = power.isSelected();
		useUrns = urnBox.isSelected();
		usePorters = porterBox.isSelected();
		useJujus = jujuBox.isSelected();
		dropGems = gemBox.isSelected();
		if(!ore.name.contains("Sandstone") && !ore.name.contains("Granite"))
			forceKeys  = forceNoClick.isSelected();
		try{
			radius = radLabel.isSelected() ? Double.parseDouble(radText.getText()) : 1000;
		}catch(NumberFormatException e){}
		center = playerStart;
	}

	@Override
	public GridPane getContentPane(Button startButton) {
		if(content != null)return content;
		content = new GridPane();
		content.setPadding(new Insets(0,3,25,3));
		content.setHgap(1.0);
		content.setVgap(1.0);


		ListView<String> oreList = new ListView<String>(); 
		oreList.setPrefWidth(167);

		FlowPane settings = new FlowPane();
		settings.setPrefWrapLength(335);

		oreList.setItems(Rock.getOres(ExiaMinerAIO.isRS3));
		oreList.getSelectionModel().clearSelection();

		oreList.getSelectionModel().selectedItemProperty().addListener(new ChangeListener<String>() {
			boolean wasSelected = true;
			public void changed(ObservableValue<? extends String> observable, String oldValue, String newValue) {
				if(newValue != null){
					startButton.setDisable(false);
					ore = Rock.getByName(newValue);
					if(newValue.equals("Granite") || newValue.equals("Sandstone")){
						wasSelected = hotkeys.isSelected();
						hotkeys.setSelected(false);
						hotkeys.setDisable(true);
					}else if(hotkeys.isDisable()){
						hotkeys.setSelected(wasSelected);
						hotkeys.setDisable(false);
					}
				}
			}
		});

		mineOne.setPadding(new Insets(10,160,0,5));
		settings.getChildren().add(mineOne);

		if(ExiaMinerAIO.isRS3){
			hotkeys.selectedProperty().addListener(new ChangeListener<Boolean>() {
				@Override
				public void changed(ObservableValue<? extends Boolean> observable, Boolean oldValue, Boolean newValue) {
					forceNoClick.setDisable(!newValue);
				}
			});

			hotkeys.setPadding(new Insets(10,160,0,5));
			settings.getChildren().add(hotkeys);

			forceNoClick.setDisable(!hotkeys.isSelected());
			forceNoClick.setPadding(new Insets(10,100,0,5));
			settings.getChildren().add(forceNoClick);

			porterBox.setPadding(new Insets(10,160,0,5));
			settings.getChildren().add(porterBox);

			urnBox.setPadding(new Insets(10,160,0,5));
			settings.getChildren().add(urnBox);

			gemBox.setPadding(new Insets(10,160,0,5));
			settings.getChildren().add(gemBox);

			jujuBox.setPadding(new Insets(10,160,0,5));
			//settings.getChildren().add(jujuBox);
}

		power.setPadding(new Insets(10,20,0,5));
		settings.getChildren().add(power);

		radLabel.selectedProperty().addListener(new ChangeListener<Boolean>() {
			@Override
			public void changed(ObservableValue<? extends Boolean> observable, Boolean oldValue, Boolean newValue) {
				radText.setDisable(!newValue);
			}
		});

		radLabel.setPadding(new Insets(0,5,0,5));
		settings.getChildren().add(radLabel);

		radText.setDisable(!radLabel.isSelected());
		radText.setMaxWidth(35.0f);
		radText.setPadding(new Insets(3,5,2,5));
		settings.getChildren().add(radText);


		Label oreLabel = new Label("Ores");
		oreLabel.setAlignment(Pos.CENTER);
		oreLabel.setPrefWidth(167);

		Label setLabel = new Label("Settings");
		setLabel.setAlignment(Pos.CENTER);
		setLabel.setPrefWidth(337);

		content.add(oreLabel, 0, 0);
		content.add(oreList, 0, 1);

		content.add(setLabel, 1, 0);
		content.add(settings, 1, 1);

		return content;
	}
}
