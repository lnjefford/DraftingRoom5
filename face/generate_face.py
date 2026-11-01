"""Generate the WFF dial geometry from one set of dimensions."""

from math import cos, pi, sin
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont


ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "face/src/main/res/raw/watchface.xml"
DRAWABLES = ROOT / "face/src/main/res/drawable"


def tick(minute: int) -> str:
    angle = minute * 2 * pi / 60 - pi / 2
    major = minute % 5 == 0
    outer = 209
    inner = 197 if major else 204
    x1, y1 = 225 + inner * cos(angle), 225 + inner * sin(angle)
    x2, y2 = 225 + outer * cos(angle), 225 + outer * sin(angle)
    color = "#FFE8E8E8" if major else "#FF454C50"
    width = 3 if major else 1.2
    return (f'            <Line startX="{x1:.2f}" startY="{y1:.2f}" '
            f'endX="{x2:.2f}" endY="{y2:.2f}"><Stroke color="{color}" '
            f'thickness="{width}" cap="ROUND" /></Line>')


def numeral(hour: int) -> str:
    angle = hour * 2 * pi / 12 - pi / 2
    x = round(225 + 174 * cos(angle) - 21)
    y = round(225 + 174 * sin(angle) - 23)
    return (f'        <PartText x="{x}" y="{y}" width="42" height="46">'
            f'<Variant mode="AMBIENT" target="alpha" value="155" />'
            f'<Text><Font family="SYNC_TO_DEVICE" size="30" '
            f'color="#FFF6F6F6">{hour}</Font></Text></PartText>')


def complication(slot_id: int, x: int, provider: str, label: str) -> str:
    return f'''        <ComplicationSlot slotId="{slot_id}" x="{x}" y="124" width="132" height="132"
            supportedTypes="SHORT_TEXT EMPTY" displayName="{label}">
            <BoundingOval x="0" y="0" width="132" height="132" />
            <DefaultProviderPolicy defaultSystemProvider="EMPTY" defaultSystemProviderType="EMPTY"
                primaryProvider="{provider}" primaryProviderType="SHORT_TEXT" />
            <Complication type="SHORT_TEXT">
                <PartText x="6" y="61" width="120" height="50">
                    <Text><Font family="SYNC_TO_DEVICE" size="30" color="#FFFFFFFF"><Template><![CDATA[%s]]><Parameter expression="[COMPLICATION.TEXT]" /></Template></Font></Text>
                </PartText>
            </Complication>
            <Complication type="EMPTY">
                <PartText x="6" y="62" width="120" height="42">
                    <Text><Font family="SYNC_TO_DEVICE" size="16" color="#FF7B8888">--</Font></Text>
                </PartText>
            </Complication>
        </ComplicationSlot>'''


lines = "\n".join(tick(minute) for minute in range(60))
numbers = "\n".join(numeral(hour) for hour in range(1, 13))
animation_frames = "\n".join(
    f'                <Image resource="weather_frame_{frame}" />'
    for frame in range(6) for _ in range(4)
)
weather = complication(1, 72, "dev.draftingroom5/dev.draftingroom5.wear.WeatherComplicationService", "weather_slot")
stand = complication(2, 246, "dev.draftingroom5/dev.draftingroom5.wear.StandComplicationService", "stand_slot")

xml = f'''<?xml version="1.0" encoding="utf-8"?>
<WatchFace width="450" height="450">
    <Metadata key="CLOCK_TYPE" value="ANALOG" />
    <Metadata key="PREVIEW_TIME" value="16:35:25" />
    <UserConfigurations>
        <ColorConfiguration id="accent" displayName="accent_label" defaultValue="mint">
            <ColorOption id="mint" displayName="accent_mint" colors="#FF69E6CA" />
            <ColorOption id="sky" displayName="accent_sky" colors="#FF8DCDF5" />
            <ColorOption id="gold" displayName="accent_gold" colors="#FFF5CD77" />
        </ColorConfiguration>
    </UserConfigurations>
    <Scene>
        <PartDraw x="0" y="0" width="450" height="450">
            <Ellipse x="0" y="0" width="450" height="450"><Fill color="#FF05090B" /></Ellipse>
            <Ellipse x="9" y="9" width="432" height="432"><Stroke color="#FF303A3D" thickness="2" /></Ellipse>
{lines}
        </PartDraw>
{numbers}
        <PartAnimatedImage x="111" y="143" width="54" height="54">
            <Variant mode="AMBIENT" target="alpha" value="0" />
            <AnimationController play="ON_VISIBLE" repeat="FALSE" beforePlaying="THUMBNAIL" afterPlaying="THUMBNAIL" />
            <SequenceImages thumbnail="weather_frame_0">
{animation_frames}
            </SequenceImages>
            <Thumbnail resource="weather_frame_0" />
        </PartAnimatedImage>
        <PartText x="72" y="238" width="132" height="26">
            <Text><Font family="SYNC_TO_DEVICE" size="15" color="#FFB8C8CC">WEATHER</Font></Text>
        </PartText>
        <PartText x="246" y="238" width="132" height="26">
            <Variant mode="AMBIENT" target="alpha" value="155" />
            <Text><Font family="SYNC_TO_DEVICE" size="15" color="[CONFIGURATION.accent]">STAND</Font></Text>
        </PartText>
{weather}
{stand}
        <PartText x="175" y="346" width="100" height="48">
            <Text><Font family="SYNC_TO_DEVICE" size="28" color="#FFF6F6F6"><Template><![CDATA[%s %d]]><Parameter expression="[DAY_OF_WEEK_S]" /><Parameter expression="[DAY]" /></Template></Font></Text>
        </PartText>
        <AnalogClock x="0" y="0" width="450" height="450">
            <HourHand resource="hour" x="214" y="83" width="22" height="170" pivotX="0.5" pivotY="0.835" tintColor="[CONFIGURATION.accent]">
                <Variant mode="AMBIENT" target="alpha" value="155" />
            </HourHand>
            <MinuteHand resource="minute" x="218" y="48" width="14" height="210" pivotX="0.5" pivotY="0.843" />
            <SecondHand resource="second" x="222" y="39" width="5" height="220" pivotX="0.5" pivotY="0.845" tintColor="[CONFIGURATION.accent]">
                <Variant mode="AMBIENT" target="alpha" value="0" />
            </SecondHand>
        </AnalogClock>
    </Scene>
</WatchFace>
'''
OUTPUT.parent.mkdir(parents=True, exist_ok=True)
OUTPUT.write_text(xml, encoding="utf-8")

# The six small, transparent frames are functional WFF resources. Drawing them
# from the same geometry keeps the animation crisp without bitmap mockup art.
DRAWABLES.mkdir(parents=True, exist_ok=True)
for frame in range(6):
    image = Image.new("RGBA", (384, 384))
    draw = ImageDraw.Draw(image)
    sun_y = 122 - (4 if frame in (2, 3, 4) else 0)
    draw.ellipse((214, sun_y - 50, 310, sun_y + 46), fill="#F5CD77")
    for dx, dy in ((0, -65), (62, 0), (50, -48)):
        draw.line((262 + dx - 8, sun_y + dy - 8, 262 + dx + 8, sun_y + dy + 8), fill="#F5CD77", width=10)
    drift = (-8, -4, 0, 4, 8, 0)[frame]
    draw.rounded_rectangle((80 + drift, 196, 280 + drift, 270), radius=36, fill="#E8F2F6")
    draw.ellipse((110 + drift, 142, 205 + drift, 244), fill="#E8F2F6")
    draw.ellipse((178 + drift, 157, 268 + drift, 247), fill="#E8F2F6")
    if frame in (3, 4):
        draw.polygon(((316, 172), (323, 185), (336, 192), (323, 199), (316, 212), (309, 199), (296, 192), (309, 185)), fill="#F5CD77")
    image.resize((96, 96), Image.Resampling.LANCZOS).save(DRAWABLES / f"weather_frame_{frame}.png", optimize=True)


def preview() -> None:
    factor = 2
    dial = Image.new("RGB", (450 * factor, 450 * factor), "#05090B")
    draw = ImageDraw.Draw(dial)
    def point(x: float, y: float) -> tuple[int, int]:
        return round(x * factor), round(y * factor)

    def font(size: int) -> ImageFont.FreeTypeFont:
        try:
            return ImageFont.truetype("C:/Windows/Fonts/arialbd.ttf", size * factor)
        except OSError:
            return ImageFont.truetype("DejaVuSans-Bold.ttf", size * factor)

    def text(value: str, x: float, y: float, size: int, color: str) -> None:
        face = font(size)
        box = draw.textbbox((0, 0), value, font=face)
        draw.text(point(x - (box[2] - box[0]) / (2 * factor), y - (box[3] - box[1]) / (2 * factor) - 5),
                  value, fill=color, font=face)

    draw.ellipse((*point(9, 9), *point(441, 441)), outline="#303A3D", width=4)
    for minute in range(60):
        angle = minute * 2 * pi / 60 - pi / 2
        major = minute % 5 == 0
        inner = 197 if major else 204
        start = point(225 + inner * cos(angle), 225 + inner * sin(angle))
        end = point(225 + 209 * cos(angle), 225 + 209 * sin(angle))
        draw.line((start, end), fill="#E8E8E8" if major else "#454C50", width=6 if major else 2)
    for hour in range(1, 13):
        angle = hour * 2 * pi / 12 - pi / 2
        text(str(hour), 225 + 174 * cos(angle), 225 + 174 * sin(angle), 30, "#F6F6F6")
    weather = Image.open(DRAWABLES / "weather_frame_0.png").resize((54 * factor, 54 * factor), Image.Resampling.LANCZOS)
    dial.paste(weather, point(111, 143), weather)
    text("64°", 138, 214, 30, "#FFFFFF")
    text("WEATHER", 138, 251, 15, "#B8C8CC")
    text("08m", 312, 214, 30, "#FFFFFF")
    text("STAND", 312, 251, 15, "#69E6CA")
    text("SUN 01", 225, 371, 28, "#F6F6F6")
    for angle, length, width, color in ((137.5, 125, 22, "#69E6CA"), (210, 170, 14, "#FFFFFF"), (150, 188, 3, "#69E6CA")):
        radians = angle * pi / 180 - pi / 2
        draw.line((point(225, 225), point(225 + length * cos(radians), 225 + length * sin(radians))),
                  fill=color, width=width * factor)
    draw.ellipse((*point(218, 218), *point(232, 232)), fill="#EAF9F6")
    dial.resize((450, 450), Image.Resampling.LANCZOS).save(DRAWABLES / "preview.png", optimize=True)


preview()
