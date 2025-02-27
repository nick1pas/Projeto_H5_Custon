package l2mv.gameserver.network.clientpackets;

import org.apache.commons.lang3.ArrayUtils;

import l2mv.commons.math.SafeMath;
import l2mv.gameserver.Config;
import l2mv.gameserver.handler.bbs.CommunityBoardManager;
import l2mv.gameserver.model.Player;
import l2mv.gameserver.model.items.ItemInstance;
import l2mv.gameserver.model.items.PcInventory;
import l2mv.gameserver.model.items.Warehouse;
import l2mv.gameserver.model.items.Warehouse.WarehouseType;
import l2mv.gameserver.network.serverpackets.components.SystemMsg;
import l2mv.gameserver.templates.item.ItemTemplate;

/**
 * Format: cdb, b - array of (dd)
 */
public class SendWareHouseDepositList extends L2GameClientPacket
{
	private static final long _WAREHOUSE_FEE = 30; // TODO [G1ta0] hardcode price

	private int _count;
	private int[] _items;
	private long[] _itemQ;

	@Override
	protected void readImpl()
	{
		this._count = this.readD();
		if (this._count * 12 > this._buf.remaining() || this._count > Short.MAX_VALUE || this._count < 1)
		{
			this._count = 0;
			return;
		}

		this._items = new int[this._count];
		this._itemQ = new long[this._count];

		for (int i = 0; i < this._count; i++)
		{
			this._items[i] = this.readD();
			this._itemQ[i] = this.readQ();
			if (this._itemQ[i] < 1 || ArrayUtils.indexOf(this._items, this._items[i]) < i)
			{
				this._count = 0;
				return;
			}
		}
	}

	@Override
	protected void runImpl()
	{
		Player activeChar = this.getClient().getActiveChar();
		if (activeChar == null || this._count == 0)
		{
			return;
		}

		if (!activeChar.getPlayerAccess().UseWarehouse || activeChar.isActionsDisabled())
		{
			activeChar.sendActionFailed();
			return;
		}

		if (activeChar.isInStoreMode())
		{
			activeChar.sendPacket(SystemMsg.WHILE_OPERATING_A_PRIVATE_STORE_OR_WORKSHOP_YOU_CANNOT_DISCARD_DESTROY_OR_TRADE_AN_ITEM);
			return;
		}

		if (activeChar.isInTrade())
		{
			activeChar.sendActionFailed();
			return;
		}

		if (activeChar.getUsingWarehouseType() == WarehouseType.FREIGHT)
		{
			this.checkAuctionAdd(activeChar, this._items, this._itemQ);
			return;
		}

		PcInventory inventory = activeChar.getInventory();
		boolean privatewh = activeChar.getUsingWarehouseType() != WarehouseType.CLAN;
		Warehouse warehouse;
		if (privatewh)
		{
			warehouse = activeChar.getWarehouse();
		}
		else
		{
			warehouse = activeChar.getClan().getWarehouse();
		}

		inventory.writeLock();
		warehouse.writeLock();
		try
		{
			int slotsleft = 0;
			long adenaDeposit = 0;

			if (privatewh)
			{
				slotsleft = activeChar.getWarehouseLimit() - warehouse.getSize();
			}
			else
			{
				slotsleft = activeChar.getClan().getWhBonus() + Config.WAREHOUSE_SLOTS_CLAN - warehouse.getSize();
			}

			int items = 0;

			// Создаем новый список передаваемых предметов, на основе полученных данных
			for (int i = 0; i < this._count; i++)
			{
				ItemInstance item = inventory.getItemByObjectId(this._items[i]);
				if (item == null || item.getCount() < this._itemQ[i] || !item.canBeStored(activeChar, privatewh || !privatewh))
				{
					this._items[i] = 0; // Обнуляем, вещь не будет передана
					this._itemQ[i] = 0L;
					continue;
				}

				if (!item.isStackable() || warehouse.getItemByItemId(item.getItemId()) == null) // вещь требует слота
				{
					if (slotsleft <= 0) // если слоты кончились нестекуемые вещи и отсутствующие стекуемые пропускаем
					{
						this._items[i] = 0; // Обнуляем, вещь не будет передана
						this._itemQ[i] = 0L;
						continue;
					}
					slotsleft--; // если слот есть то его уже нет
				}

				if (item.getItemId() == ItemTemplate.ITEM_ID_ADENA)
				{
					adenaDeposit = this._itemQ[i];
				}

				items++;
			}

			// Сообщаем о том, что слоты кончились
			if (slotsleft <= 0)
			{
				activeChar.sendPacket(SystemMsg.YOUR_WAREHOUSE_IS_FULL);
			}

			if (items == 0)
			{
				activeChar.sendPacket(SystemMsg.INCORRECT_ITEM_COUNT);
				return;
			}

			// Проверяем, хватит ли у нас денег на уплату налога
			long fee = SafeMath.mulAndCheck(items, _WAREHOUSE_FEE);

			if (fee + adenaDeposit > activeChar.getAdena())
			{
				activeChar.sendPacket(SystemMsg.YOU_LACK_THE_FUNDS_NEEDED_TO_PAY_FOR_THIS_TRANSACTION);
				return;
			}

			if (!activeChar.reduceAdena(fee, true, (privatewh ? "Private" : "Clan") + "WarehouseDepositFee"))
			{
				activeChar.sendPacket(SystemMsg.YOU_DO_NOT_HAVE_ENOUGH_ADENA);
				return;
			}

			for (int i = 0; i < this._count; i++)
			{
				if (this._items[i] == 0)
				{
					continue;
				}
				ItemInstance item = inventory.removeItemByObjectId(this._items[i], this._itemQ[i], (privatewh ? "Private" : "Clan") + "WarehouseDeposit");
				warehouse.addItem(item, null, null);
			}
		}
		catch (ArithmeticException ae)
		{
			// TODO audit
			activeChar.sendPacket(SystemMsg.YOU_HAVE_EXCEEDED_THE_QUANTITY_THAT_CAN_BE_INPUTTED);
			return;
		}
		finally
		{
			warehouse.writeUnlock();
			inventory.writeUnlock();
		}

		// Обновляем параметры персонажа
		activeChar.sendChanges();
		activeChar.sendPacket(SystemMsg.THE_TRANSACTION_IS_COMPLETE);
	}

	private void checkAuctionAdd(Player activeChar, int[] _items, long[] _itemQ)
	{
		if (_items.length != 1 || _itemQ[0] != 1)
		{
			activeChar.sendMessage("You can add just one item at the time!");
			return;
		}

		CommunityBoardManager.getInstance().getCommunityHandler("_sendTimePrice_").onBypassCommand(activeChar, "_sendTimePrice_" + _items[0]);
	}
}