package dev.example.mse;

import java.util.List;
import java.util.Map;

/** Описание API Minescript v5.0 (сгенерировано по документации minescript.net/docs). */
final class McApi {
    private McApi() {}

    record Fn(String name, String sig, String ret) {}
    record Member(String name, String kind, String detail, String ret) {}

    static final Fn[] FUNCTIONS = {
        new Fn("execute", "execute(command: str)", ""),
        new Fn("echo", "echo(*messages)", ""),
        new Fn("echo_json", "echo_json(json_text)", ""),
        new Fn("chat", "chat(*messages)", ""),
        new Fn("log", "log(*messages)", ""),
        new Fn("screenshot", "screenshot(filename: str=None)", ""),
        new Fn("job_info", "job_info() -> List[JobInfo]", "List[JobInfo]"),
        new Fn("flush", "flush()", ""),
        new Fn("player_name", "player_name() -> str", "str"),
        new Fn("player_position", "player_position() -> List[float]", "List[float]"),
        new Fn("player_hand_items", "player_hand_items() -> HandItems", "HandItems"),
        new Fn("player_inventory", "player_inventory() -> List[ItemStack]", "List[ItemStack]"),
        new Fn("player_inventory_slot_to_hotbar", "player_inventory_slot_to_hotbar(slot: int) -> int", "int"),
        new Fn("player_inventory_select_slot", "player_inventory_select_slot(slot: int) -> int", "int"),
        new Fn("press_key_bind", "press_key_bind(key_mapping_name: str, pressed: bool)", ""),
        new Fn("player_press_forward", "player_press_forward(pressed: bool)", ""),
        new Fn("player_press_backward", "player_press_backward(pressed: bool)", ""),
        new Fn("player_press_left", "player_press_left(pressed: bool)", ""),
        new Fn("player_press_right", "player_press_right(pressed: bool)", ""),
        new Fn("player_press_jump", "player_press_jump(pressed: bool)", ""),
        new Fn("player_press_sprint", "player_press_sprint(pressed: bool)", ""),
        new Fn("player_press_sneak", "player_press_sneak(pressed: bool)", ""),
        new Fn("player_press_pick_item", "player_press_pick_item(pressed: bool)", ""),
        new Fn("player_press_use", "player_press_use(pressed: bool)", ""),
        new Fn("player_press_attack", "player_press_attack(pressed: bool)", ""),
        new Fn("player_press_swap_hands", "player_press_swap_hands(pressed: bool)", ""),
        new Fn("player_press_drop", "player_press_drop(pressed: bool)", ""),
        new Fn("player_orientation", "player_orientation() -> List[float]", "List[float]"),
        new Fn("player_set_orientation", "player_set_orientation(yaw: float, pitch: float) -> bool", "bool"),
        new Fn("player_get_targeted_block", "player_get_targeted_block(max_distance: float = 20) -> TargetedBlock", "TargetedBlock"),
        new Fn("player_get_targeted_entity", "player_get_targeted_entity(max_distance: float = 20, nbt: bool = False) -> EntityData", "EntityData"),
        new Fn("player_health", "player_health() -> float", "float"),
        new Fn("player", "player(*, nbt: bool = False) -> EntityData", "EntityData"),
        new Fn("players", "players(*, nbt: bool = False, uuid: str = None, name: str = None, position: Vector3f = None, offset: Vector3f = None, min_distance: float = None, max_distance: float = None, sort: str = None, limit: int = None) -> List[EntityData]", "List[EntityData]"),
        new Fn("entities", "entities(*, nbt: bool = False, uuid: str = None, name: str = None, type: str = None, position: Vector3f = None, offset: Vector3f = None, min_distance: float = None, max_distance: float = None, sort: str = None, limit: int = None) -> List[EntityData]", "List[EntityData]"),
        new Fn("version_info", "version_info() -> VersionInfo", "VersionInfo"),
        new Fn("world_info", "world_info() -> WorldInfo", "WorldInfo"),
        new Fn("get_scoreboard", "get_scoreboard() -> ScoreboardData", "ScoreboardData"),
        new Fn("getblock", "getblock(x: int, y: int, z: int) -> str", "str"),
        new Fn("getblocklist", "getblocklist(positions: List[List[int]]) -> List[str]", "List[str]"),
        new Fn("get_block_region", "get_block_region(pos1: BlockPos, pos2: BlockPos, safety_limit: bool = True) -> BlockRegion", "BlockRegion"),
        new Fn("await_loaded_region", "await_loaded_region(x1: int, z1: int, x2: int, z2: int)", ""),
        new Fn("set_default_executor", "set_default_executor(executor: minescript_runtime.FunctionExecutor)", ""),
        new Fn("screen_name", "screen_name() -> str", "str"),
        new Fn("show_chat_screen", "show_chat_screen(show: bool, prompt: str = None) -> bool", "bool"),
        new Fn("append_chat_history", "append_chat_history(message: str)", ""),
        new Fn("chat_input", "chat_input() -> List[Union[str, int]]", "List[Union[str, int]]"),
        new Fn("set_chat_input", "set_chat_input(text: str = None, position: int = None, color: int = None)", ""),
        new Fn("container_get_items", "container_get_items() -> List[ItemStack]", "List[ItemStack]"),
        new Fn("player_look_at", "player_look_at(x: float, y: float, z: float)", ""),
        new Fn("combine_rotations", "combine_rotations(rot1: Rotation, rot2: Rotation, /) -> Rotation", "Rotation"),
        new Fn("get_block", "get_block(x: int, y: int, z: int) -> str", "str"),
        new Fn("get_block_list", "get_block_list(positions: List[List[int]]) -> List[str]", "List[str]")
    };

    /** Классы и объекты верхнего уровня модуля minescript. */
    static final String[][] CLASSES = {
        {"EventQueue", "EventQueue()  # with EventQueue() as q: q.register_chat_listener(); q.get()", "EventQueue"},
        {"EventLoop", "EventLoop()  # Pyjinn only", ""},
        {"EventType", "EventType.CHAT / KEY / MOUSE / ADD_ENTITY / BLOCK_UPDATE ...", ""},
        {"BlockRegion", "BlockRegion(min_pos, max_pos, blocks)", "BlockRegion"},
        {"BlockPack", "BlockPack.read_world(...) / read_file(...)", ""},
        {"BlockPacker", "BlockPacker()", ""},
        {"Rotations", "Rotations.IDENTITY / X_90 / Y_90 ...", ""},
        {"tick_loop", "executor: once per game tick (20/s)", ""},
        {"render_loop", "executor: once per frame (default)", ""},
        {"script_loop", "executor: as fast as possible (may be unstable)", ""},
    };

    private static Member f(String n, String t) { return new Member(n, "fld", n + ": " + t, t); }
    private static Member m(String n, String sig, String ret) { return new Member(n, "mth", sig, ret); }

    static final Map<String, List<Member>> TYPES = Map.ofEntries(
        Map.entry("EntityData", List.of(
            f("name", "str"), f("type", "str"), f("uuid", "str"), f("id", "int"),
            f("position", "Vector3f"), f("yaw", "float"), f("pitch", "float"), f("velocity", "Vector3f"),
            f("lerp_position", "Vector3f"), f("health", "float"), f("local", "bool"),
            f("passengers", "List[str]"), f("nbt", "str"))),
        Map.entry("TargetedBlock", List.of(
            f("position", "BlockPos"), f("distance", "float"), f("side", "str"), f("type", "str"))),
        Map.entry("HandItems", List.of(f("main_hand", "ItemStack"), f("off_hand", "ItemStack"))),
        Map.entry("ItemStack", List.of(
            f("item", "str"), f("count", "int"), f("nbt", "str"), f("slot", "int"), f("selected", "bool"))),
        Map.entry("WorldInfo", List.of(
            f("game_ticks", "int"), f("day_ticks", "int"), f("raining", "bool"), f("thundering", "bool"),
            f("spawn", "BlockPos"), f("hardcore", "bool"), f("difficulty", "str"), f("name", "str"),
            f("address", "str"), f("dimension", "str"))),
        Map.entry("VersionInfo", List.of(
            f("minecraft", "str"), f("minescript", "str"), f("mod_loader", "str"), f("launcher", "str"),
            f("os_name", "str"), f("os_version", "str"), f("minecraft_class_name", "str"), f("pyjinn", "str"))),
        Map.entry("ScoreboardData", List.of(
            f("objective_name", "str"), f("display_name", "str"), f("entries", "List[ScoreboardEntry]"))),
        Map.entry("ScoreboardEntry", List.of(f("name", "str"), f("score", "int"), f("display_name", "str"))),
        Map.entry("JobInfo", List.of(
            f("job_id", "int"), f("command", "List[str]"), f("source", "str"), f("status", "str"),
            f("parent_job_id", "int"), f("self", "bool"))),
        Map.entry("BlockRegion", List.of(
            f("min_pos", "BlockPos"), f("max_pos", "BlockPos"), f("blocks", "tuple"),
            m("get_block", "get_block(x: int, y: int, z: int) -> str", "str"),
            m("get_index", "get_index(x: int, y: int, z: int) -> int", "int"))),
        Map.entry("EventQueue", List.of(
            m("get", "get(block: bool = True, timeout: float = None) -> Event", "Event"),
            m("register_chat_listener", "register_chat_listener()  # incoming chat messages", ""),
            m("register_outgoing_chat_interceptor", "register_outgoing_chat_interceptor(*, prefix: str = None, pattern: str = None)", ""),
            m("register_key_listener", "register_key_listener()", ""),
            m("register_mouse_listener", "register_mouse_listener()", ""),
            m("register_add_entity_listener", "register_add_entity_listener()", ""),
            m("register_block_update_listener", "register_block_update_listener()", ""),
            m("register_take_item_listener", "register_take_item_listener()", ""),
            m("register_damage_listener", "register_damage_listener()", ""),
            m("register_explosion_listener", "register_explosion_listener()", ""),
            m("register_chunk_listener", "register_chunk_listener()", ""),
            m("register_world_listener", "register_world_listener()", ""))),
        // событие любого типа: разные типы событий имеют разные поля
        Map.entry("Event", List.of(
            f("type", "str"), f("time", "float"), f("message", "str"),
            f("key", "int"), f("scan_code", "int"), f("action", "int"), f("modifiers", "int"), f("screen", "str"),
            f("button", "int"), f("x", "float"), f("y", "float"),
            f("position", "Vector3f"), f("old_state", "str"), f("new_state", "str"),
            f("entity", "EntityData"), f("player_uuid", "str"), f("item", "EntityData"), f("amount", "int"),
            f("entity_uuid", "str"), f("cause_uuid", "str"), f("source", "str"), f("blockpack_base64", "str"),
            f("loaded", "bool"), f("x_min", "int"), f("z_min", "int"), f("x_max", "int"), f("z_max", "int"),
            f("connected", "bool"))),
        Map.entry("EventType", List.of(
            f("CHAT", "str"), f("KEY", "str"), f("MOUSE", "str"), f("OUTGOING_CHAT_INTERCEPT", "str"),
            f("ADD_ENTITY", "str"), f("BLOCK_UPDATE", "str"), f("TAKE_ITEM", "str"), f("DAMAGE", "str"),
            f("EXPLOSION", "str"), f("CHUNK", "str"), f("WORLD", "str")))
    );
}
