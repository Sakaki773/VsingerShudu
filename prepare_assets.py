from __future__ import annotations

from pathlib import Path

from PIL import Image, ImageEnhance, ImageFilter


BASE_DIR = Path(__file__).resolve().parent
ASSET_DIR = BASE_DIR / "assets"
HERO = ASSET_DIR / "luotianyi_hero.jpg"
SOFT = ASSET_DIR / "luotianyi_soft.jpg"
SELECTED_DIR = ASSET_DIR / "selected"
ORIGINALS_DIR = ASSET_DIR / "originals"
IMAGE_SUFFIXES = {".jpg", ".jpeg", ".png", ".webp"}

TARGETS = {
    "pc": {
        "welcome": (1120, 1840),
        "background": (1120, 1840),
    },
    "android": {
        "welcome": (1080, 1920),
        "background": (1080, 1920),
    },
}


def cover(image: Image.Image, size: tuple[int, int], anchor: tuple[float, float] = (0.5, 0.5)) -> Image.Image:
    src_w, src_h = image.size
    dst_w, dst_h = size
    scale = max(dst_w / src_w, dst_h / src_h)
    resized = image.resize((round(src_w * scale), round(src_h * scale)), Image.Resampling.LANCZOS)
    max_left = max(0, resized.width - dst_w)
    max_top = max(0, resized.height - dst_h)
    left = round(max_left * anchor[0])
    top = round(max_top * anchor[1])
    return resized.crop((left, top, left + dst_w, top + dst_h))


def soften_for_game(image: Image.Image, size: tuple[int, int]) -> Image.Image:
    cropped = cover(image, size, anchor=(0.5, 0.45))
    cropped = cropped.filter(ImageFilter.GaussianBlur(radius=9))
    cropped = ImageEnhance.Color(cropped).enhance(0.9)
    cropped = ImageEnhance.Brightness(cropped).enhance(1.08)
    white = Image.new("RGB", size, "#FFFFFF")
    return Image.blend(cropped, white, 0.48)


def make_welcome(image: Image.Image, size: tuple[int, int]) -> Image.Image:
    cropped = cover(image, size, anchor=(0.5, 0.44))
    cropped = ImageEnhance.Color(cropped).enhance(0.96)
    cropped = ImageEnhance.Contrast(cropped).enhance(1.03)
    return cropped


def source_images() -> list[Path]:
    selected = sorted(p for p in SELECTED_DIR.glob("*") if p.suffix.lower() in IMAGE_SUFFIXES)
    if selected:
        return selected

    originals = sorted(p for p in ORIGINALS_DIR.glob("*") if p.suffix.lower() in IMAGE_SUFFIXES)
    if originals:
        return originals

    fallback = [p for p in (HERO, SOFT) if p.exists()]
    if fallback:
        return fallback

    raise FileNotFoundError("Missing source images in assets/selected, assets/originals, or fallback files")


def clean_generated(out_dir: Path) -> None:
    for pattern in ("welcome_*.jpg", "game_background_*.jpg"):
        for path in out_dir.glob(pattern):
            path.unlink()


def main() -> None:
    sources = source_images()
    print("using source images:")
    for source in sources:
        print(f"  {source}")

    for family, specs in TARGETS.items():
        out_dir = ASSET_DIR / family
        out_dir.mkdir(parents=True, exist_ok=True)
        clean_generated(out_dir)

        for index, source in enumerate(sources, start=1):
            image = Image.open(source).convert("RGB")
            welcome = make_welcome(image, specs["welcome"])
            background = soften_for_game(image, specs["background"])
            welcome_path = out_dir / f"welcome_{index:02d}.jpg"
            background_path = out_dir / f"game_background_{index:02d}.jpg"
            welcome.save(welcome_path, quality=92, optimize=True)
            background.save(background_path, quality=90, optimize=True)
            print(f"generated {welcome_path} {welcome.size}")
            print(f"generated {background_path} {background.size}")

            if index == 1:
                welcome.save(out_dir / "welcome.jpg", quality=92, optimize=True)
                background.save(out_dir / "game_background.jpg", quality=90, optimize=True)


if __name__ == "__main__":
    main()
