#!/usr/bin/env python3
"""
Utility script that fills the SQLite database with synthetic warehouse movements.
Generates one-year history of inbound, outbound, and transfer operations for
all products so the forecasting module has enough data.
"""

from __future__ import annotations

import argparse
import math
import random
import sqlite3
from datetime import date, datetime, timedelta
from typing import Dict, Iterable, List, Tuple

TAG = "[SYNTHETIC_FORECAST]"


def parse_date(value: str) -> date:
    return datetime.strptime(value, "%Y-%m-%d").date()


def month_iterator(start: date, end: date) -> Iterable[date]:
    current = start.replace(day=1)
    while current <= end:
        yield current
        current = (current.replace(day=28) + timedelta(days=4)).replace(day=1)


def week_iterator(start: date, end: date) -> Iterable[date]:
    current = start - timedelta(days=start.weekday())
    while current <= end:
        yield current
        current += timedelta(weeks=1)


def insert_movement(
    cursor: sqlite3.Cursor,
    movement_date: date,
    info: str,
    movement_type: str,
    warehouse_id: int,
    employee_id: int,
    counterparty_id: int | None = None,
    target_employee: int | None = None,
    target_warehouse: int | None = None,
    rng: random.Random | None = None,
) -> int:
    rng = rng or random
    timestamp = datetime.combine(
        movement_date, datetime.min.time()
    ) + timedelta(hours=rng.randint(8, 19), minutes=rng.randint(0, 59))
    cursor.execute(
        """
        INSERT INTO movements(date, info, type, counterparty_id, employee_id,
                              target_employee_id, target_warehouse_id, warehouse_id)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        """,
        (
            timestamp.strftime("%Y-%m-%d %H:%M:%S"),
            info,
            movement_type,
            counterparty_id,
            employee_id,
            target_employee,
            target_warehouse,
            warehouse_id,
        ),
    )
    return cursor.lastrowid


def insert_position(cursor: sqlite3.Cursor, movement_id: int, product_id: int, quantity: int) -> None:
    cursor.execute(
        "INSERT INTO products_movement(quantity, movement_id, product_id) VALUES (?, ?, ?)",
        (quantity, movement_id, product_id),
    )


def fetch_table_ids(cursor: sqlite3.Cursor, table: str) -> List[int]:
    cursor.execute(f"SELECT id FROM {table} ORDER BY id")
    return [row[0] for row in cursor.fetchall()]


def fetch_products(cursor: sqlite3.Cursor, product_ids: List[int] | None) -> Dict[int, str]:
    if product_ids:
        placeholders = ",".join("?" for _ in product_ids)
        cursor.execute(
            f"SELECT id, name FROM products WHERE id IN ({placeholders}) ORDER BY id",
            product_ids,
        )
    else:
        cursor.execute("SELECT id, name FROM products ORDER BY id")
    data = dict(cursor.fetchall())
    if not data:
        raise RuntimeError("No products found in DB.")
    if product_ids:
        missing = set(product_ids) - data.keys()
        if missing:
            raise RuntimeError(f"Products not found in DB: {sorted(missing)}")
    return data


def build_profiles(products: Dict[int, str], seed: int) -> Dict[int, Dict[str, float]]:
    profiles: Dict[int, Dict[str, float]] = {}
    for product_id in products:
        rng = random.Random(seed + product_id * 97)
        base = rng.uniform(8, 26)
        trend = rng.uniform(0.002, 0.01)
        if rng.random() < 0.25:
            trend *= -1
        profiles[product_id] = {
            "base": base,
            "trend": trend,
            "season_amp": rng.uniform(3, 10),
            "season_phase": rng.uniform(0, 2 * math.pi),
            "noise": rng.uniform(1.5, 4.5),
            "ship_prob": rng.uniform(0.4, 0.7),
            "transfer_prob": rng.uniform(0.12, 0.28),
            "supply_factor": rng.uniform(0.9, 1.2),
        }
    return profiles


def cleanup(cursor: sqlite3.Cursor, wipe_all: bool) -> None:
    if wipe_all:
        cursor.execute("DELETE FROM products_movement")
        cursor.execute("DELETE FROM movements")
        return
    cursor.execute(
        "DELETE FROM products_movement WHERE movement_id IN (SELECT id FROM movements WHERE info LIKE ?)",
        (f"%{TAG}%",),
    )
    cursor.execute("DELETE FROM movements WHERE info LIKE ?", (f"%{TAG}%",))


def pick_two(items: List[int], rng: random.Random) -> Tuple[int, int]:
    if len(items) < 2:
        return items[0], items[0]
    first = rng.choice(items)
    second = rng.choice([item for item in items if item != first])
    return first, second


def generate_inbound(
    cursor: sqlite3.Cursor,
    product_names: Dict[int, str],
    profiles: Dict[int, Dict[str, float]],
    warehouses: List[int],
    employees: List[int],
    suppliers: List[int],
    start: date,
    end: date,
    rng: random.Random,
) -> int:
    count = 0
    for first_day in month_iterator(start, end):
        for product_id, name in product_names.items():
            profile = profiles[product_id]
            qty = int((profile["base"] * 28) * profile["supply_factor"] * (0.8 + rng.random() * 0.4))
            qty = max(1, qty)
            info = f"{TAG} Поставка {name}"
            movement_id = insert_movement(
                cursor,
                first_day,
                info,
                "INBOUND",
                rng.choice(warehouses),
                rng.choice(employees),
                rng.choice(suppliers) if suppliers else None,
                rng=rng,
            )
            insert_position(cursor, movement_id, product_id, qty)
            count += 1
    return count


def generate_outbound(
    cursor: sqlite3.Cursor,
    product_names: Dict[int, str],
    profiles: Dict[int, Dict[str, float]],
    warehouses: List[int],
    employees: List[int],
    customers: List[int],
    start: date,
    end: date,
    rng: random.Random,
) -> int:
    count = 0
    current = start
    while current <= end:
        day_index = (current - start).days
        seasonal_angle = 2 * math.pi * (current.timetuple().tm_yday / 365.0)
        for product_id, name in product_names.items():
            profile = profiles[product_id]
            demand = (
                profile["base"]
                + profile["trend"] * day_index
                + profile["season_amp"] * math.sin(seasonal_angle + profile["season_phase"])
                + rng.gauss(0, profile["noise"])
            )
            demand = max(0, int(round(demand)))
            if demand == 0 or rng.random() > profile["ship_prob"]:
                continue
            info = f"{TAG} Отгрузка {name}"
            movement_id = insert_movement(
                cursor,
                current,
                info,
                "OUTBOUND",
                rng.choice(warehouses),
                rng.choice(employees),
                rng.choice(customers) if customers else None,
                rng=rng,
            )
            insert_position(cursor, movement_id, product_id, max(1, demand))
            count += 1
        current += timedelta(days=1)
    return count


def generate_transfers(
    cursor: sqlite3.Cursor,
    product_names: Dict[int, str],
    profiles: Dict[int, Dict[str, float]],
    warehouses: List[int],
    employees: List[int],
    start: date,
    end: date,
    rng: random.Random,
) -> int:
    count = 0
    for week_start in week_iterator(start, end):
        for product_id, name in product_names.items():
            if rng.random() > profiles[product_id]["transfer_prob"]:
                continue
            transfer_date = week_start + timedelta(days=rng.randint(0, 6))
            if transfer_date < start or transfer_date > end:
                continue
            source, target = pick_two(warehouses, rng)
            qty = max(1, int(profiles[product_id]["base"] * rng.uniform(0.2, 0.6)))
            info = f"{TAG} Трансфер {name}"
            movement_id = insert_movement(
                cursor,
                transfer_date,
                info,
                "TRANSFER",
                source,
                rng.choice(employees),
                None,
                target_employee=rng.choice(employees),
                target_warehouse=target,
                rng=rng,
            )
            insert_position(cursor, movement_id, product_id, qty)
            count += 1
    return count


def populate(
    db_path: str,
    seed: int,
    start: date,
    end: date,
    product_ids: List[int] | None,
    wipe_all: bool,
) -> Tuple[int, int, int]:
    rng = random.Random(seed)
    with sqlite3.connect(db_path) as conn:
        cursor = conn.cursor()
        products = fetch_products(cursor, product_ids)
        profiles = build_profiles(products, seed)
        warehouses = fetch_table_ids(cursor, "warehouse")
        employees = fetch_table_ids(cursor, "employees")
        counterparties = fetch_table_ids(cursor, "counterparties")
        if not warehouses or not employees:
            raise RuntimeError("Warehouses/employees missing in DB.")
        midpoint = max(1, len(counterparties) // 2)
        suppliers = counterparties[:midpoint]
        customers = counterparties[midpoint:] or counterparties
        cleanup(cursor, wipe_all)
        inbound = generate_inbound(cursor, products, profiles, warehouses, employees, suppliers, start, end, rng)
        outbound = generate_outbound(cursor, products, profiles, warehouses, employees, customers, start, end, rng)
        transfers = generate_transfers(cursor, products, profiles, warehouses, employees, start, end, rng)
        conn.commit()
        return inbound, outbound, transfers


def parse_product_ids(value: str) -> List[int]:
    return [int(item.strip()) for item in value.split(",") if item.strip()]


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Fill kpo.db with reproducible synthetic movements for forecasting demos."
    )
    parser.add_argument("--db", default="kpo.db", help="Path to SQLite database (default: kpo.db)")
    parser.add_argument(
        "--seed",
        type=int,
        default=42,
        help="Random seed to keep generated history stable (default: 42)",
    )
    parser.add_argument(
        "--start",
        type=parse_date,
        default=(date.today() - timedelta(days=364)),
        help="Start date YYYY-MM-DD (default: last 365 days)",
    )
    parser.add_argument(
        "--end",
        type=parse_date,
        default=date.today(),
        help="End date YYYY-MM-DD (default: today)",
    )
    parser.add_argument(
        "--products",
        type=parse_product_ids,
        default=None,
        help="Comma-separated product IDs (default: all products)",
    )
    parser.add_argument(
        "--wipe-all",
        action="store_true",
        help="Delete all movements before inserting synthetic data",
    )
    args = parser.parse_args()
    inbound, outbound, transfers = populate(
        args.db,
        args.seed,
        args.start,
        args.end,
        args.products,
        args.wipe_all,
    )
    print(
        "Inserted {inbound} inbound, {outbound} outbound, {transfers} transfer synthetic movements into {db}."
        .format(inbound=inbound, outbound=outbound, transfers=transfers, db=args.db)
    )
    print(f"Range: {args.start} → {args.end}")


if __name__ == "__main__":
    main()
